package com.fsts.trace.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端真实 IP 解析。
 *
 * <p>限流的键必须落在"真实来源"上，否则在 Nginx 反向代理后所有请求的
 * remoteAddr 都是代理地址，会把全站当成同一个 IP 限流（一限全限）。
 * 因此优先读取 X-Forwarded-For 的第一段。
 */
public final class ClientIpUtils {

    private static final String UNKNOWN = "unknown";

    private ClientIpUtils() {
    }

    public static String resolve(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (isValid(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (isValid(ip)) {
            return ip.trim();
        }
        ip = request.getHeader("Proxy-Client-IP");
        if (isValid(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private static boolean isValid(String ip) {
        return ip != null && !ip.isBlank() && !UNKNOWN.equalsIgnoreCase(ip);
    }
}
