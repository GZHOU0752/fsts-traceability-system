package com.fsts.trace.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

/**
 * 客户端真实 IP 解析。
 *
 * <p>限流的键必须落在"真实来源"上，否则在 Nginx 反向代理后所有请求的
 * remoteAddr 都是代理地址，会把全站当成同一个 IP 限流（一限全限）。
 *
 * <p><b>为什么不能直接取 X-Forwarded-For 的第一段：</b>
 * 该请求头由客户端自行携带，任何人都能伪造。若无条件采信，攻击者只需每次
 * 换一个 X-Forwarded-For 就能拿到全新的令牌桶，按 IP 限流形同虚设；
 * 同理也会让 {@code /api/public/access-hosts} 的内网守卫被绕过。
 *
 * <p>因此采用"可信跳"规则：
 * <ol>
 *   <li>直连对端（remoteAddr）不是内网/回环地址时，一律以 remoteAddr 为准，
 *       完全不看转发头 —— 公网直连场景下转发头永远不可信；</li>
 *   <li>直连对端可信时，从 X-Forwarded-For 的<b>最右侧</b>开始向左找第一个
 *       "非可信地址"：右侧条目由更靠近服务端的代理写入，客户端无法伪造
 *       （Nginx 的 {@code $proxy_add_x_forwarded_for} 是追加而非覆盖）。</li>
 * </ol>
 */
public final class ClientIpUtils {

    /** 仅用于识别字面量，避免把任意字符串交给 InetAddress 触发 DNS 解析 */
    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(?:\\.\\d{1,3}){3}$");
    private static final Pattern IPV6_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private ClientIpUtils() {
    }

    public static String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedPeer(remoteAddr)) {
            return remoteAddr;
        }
        String forwarded = rightmostUntrusted(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) {
            return forwarded;
        }
        forwarded = firstLiteral(request.getHeader("X-Real-IP"));
        if (forwarded != null) {
            return forwarded;
        }
        forwarded = firstLiteral(request.getHeader("Proxy-Client-IP"));
        return forwarded != null ? forwarded : remoteAddr;
    }

    /**
     * 该地址是否属于"可信跳"（回环 / 私有 / 链路本地 / 未指定地址）。
     *
     * <p>判断只做字符串与网段比较，不调用 {@code InetAddress.getByName}，
     * 避免对攻击者可控的请求头做 DNS 解析（既慢又可能被用于探测内网）。
     */
    public static boolean isTrustedPeer(String ip) {
        String value = normalize(ip);
        if (value == null) {
            return false;
        }
        if (IPV4_LITERAL.matcher(value).matches()) {
            return isPrivateIpv4(value);
        }
        if (!value.contains(":") || !IPV6_LITERAL.matcher(value).matches()) {
            return false;
        }
        String lower = value.toLowerCase();
        if (lower.equals("::") || lower.equals("::1")) {
            return true;
        }
        // ::ffff:a.b.c.d —— IPv4 映射地址，按 IPv4 规则判断
        int mapped = lower.indexOf("::ffff:");
        if (mapped >= 0) {
            String v4 = value.substring(mapped + 7);
            return IPV4_LITERAL.matcher(v4).matches() && isPrivateIpv4(v4);
        }
        // fc00::/7 唯一本地地址、fe80::/10 链路本地地址
        return lower.startsWith("fc") || lower.startsWith("fd")
                || lower.startsWith("fe8") || lower.startsWith("fe9")
                || lower.startsWith("fea") || lower.startsWith("feb");
    }

    /**
     * 从右向左找第一个不可信地址；全是可信跳或没有合法地址时返回 null。
     */
    static String rightmostUntrusted(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String[] parts = header.split(",");
        for (int index = parts.length - 1; index >= 0; index--) {
            String candidate = firstLiteral(parts[index]);
            if (candidate != null && !isTrustedPeer(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 取单个值中的第一个合法 IP 字面量，非法值（含 "unknown"）返回 null。
     */
    static String firstLiteral(String raw) {
        String value = normalize(raw);
        if (value == null) {
            return null;
        }
        return isIpLiteral(value) ? value : null;
    }

    private static boolean isIpLiteral(String value) {
        return IPV4_LITERAL.matcher(value).matches()
                || (value.contains(":") && IPV6_LITERAL.matcher(value).matches());
    }

    /**
     * 去掉 [..] 包裹、IPv6 区域后缀（%eth0）与 IPv4 端口后缀。
     */
    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || "unknown".equalsIgnoreCase(value)) {
            return null;
        }
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            return end > 1 ? value.substring(1, end) : null;
        }
        int zone = value.indexOf('%');
        if (zone > 0) {
            value = value.substring(0, zone);
        }
        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(':') == colon) {
            String host = value.substring(0, colon);
            String port = value.substring(colon + 1);
            if (IPV4_LITERAL.matcher(host).matches() && port.chars().allMatch(Character::isDigit)) {
                return host;
            }
        }
        return value;
    }

    /**
     * 回环 127/8、私有 10/8、172.16/12、192.168/16、链路本地 169.254/16、未指定 0.0.0.0。
     */
    private static boolean isPrivateIpv4(String value) {
        String[] parts = value.split("\\.");
        int first;
        int second;
        try {
            first = Integer.parseInt(parts[0]);
            second = Integer.parseInt(parts[1]);
            for (String part : parts) {
                int segment = Integer.parseInt(part);
                if (segment > 255) {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        if (first == 127 || first == 10 || first == 0) {
            return true;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        if (first == 192 && second == 168) {
            return true;
        }
        return first == 169 && second == 254;
    }
}
