package com.fsts.trace.support.network;

import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 局域网地址探测器：为"手机扫码打开追溯页"提供可访问的主机地址。
 *
 * <p>消费者扫的二维码里必须是手机能打开的地址。开发联调时前端跑在 localhost 上，
 * 而 localhost 对手机来说指向手机自己，扫了必然打不开；页面自身也无法得知
 * 所在机器的内网 IP，因此由服务端探测后下发给前端。
 *
 * <p>挑地址的关键在排序而不是穷举：开发机上往往同时存在 VMware、WSL、Docker、
 * Tailscale 等虚拟网卡，这些地址对手机不可达，必须排在真实网卡之后。
 */
@Slf4j
public final class LanAddressResolver {

    /** 虚拟网卡 / 隧道网卡关键字：命中后直接落到最后 */
    private static final String[] VIRTUAL_KEYWORDS = {
            "vmware", "virtualbox", "hyper-v", "vethernet", "virtual", "docker", "wsl",
            "loopback", "tailscale", "zerotier", "bluetooth", "npcap", "tap", "tun", "vpn"
    };

    /** 无线网卡关键字：手机与电脑通常连同一个 Wi-Fi，优先返回 */
    private static final String[] WIRELESS_KEYWORDS = {"wi-fi", "wifi", "wlan", "wireless", "无线", "wlp"};

    /** 有线网卡关键字 */
    private static final String[] WIRED_KEYWORDS = {"ethernet", "以太网", "本地连接", "eth", "en0", "enp", "ens"};

    private static final int MAX_RESULT = 8;

    private LanAddressResolver() {
    }

    /**
     * @return 按"最可能是真实网卡"排序的内网 IPv4 地址，取不到时返回空列表
     */
    public static List<String> resolve() {
        List<Map.Entry<Integer, String>> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!isCandidate(networkInterface)) {
                    continue;
                }
                int score = score(networkInterface.getDisplayName());
                for (InterfaceAddress address : networkInterface.getInterfaceAddresses()) {
                    InetAddress ip = address.getAddress();
                    if (ip instanceof Inet4Address && !ip.isLoopbackAddress()
                            && !ip.isLinkLocalAddress() && ip.isSiteLocalAddress()) {
                        candidates.add(new AbstractMap.SimpleEntry<>(score, ip.getHostAddress()));
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("枚举本机网卡失败，扫码链接将退回本机地址: {}", e.getMessage());
        }

        candidates.sort(Map.Entry.<Integer, String>comparingByKey(Comparator.reverseOrder()));
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (Map.Entry<Integer, String> candidate : candidates) {
            distinct.add(candidate.getValue());
        }
        List<String> result = new ArrayList<>(distinct);
        return result.size() > MAX_RESULT ? List.copyOf(result.subList(0, MAX_RESULT)) : List.copyOf(result);
    }

    private static boolean isCandidate(NetworkInterface networkInterface) throws SocketException {
        return networkInterface.isUp() && !networkInterface.isLoopback() && !networkInterface.isPointToPoint();
    }

    /**
     * 网卡名打分：分数越高越可能是手机能访问的网卡。
     */
    static int score(String displayName) {
        String name = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        if (containsAny(name, VIRTUAL_KEYWORDS)) {
            return -100;
        }
        if (containsAny(name, WIRELESS_KEYWORDS)) {
            return 50;
        }
        if (containsAny(name, WIRED_KEYWORDS)) {
            return 40;
        }
        return 0;
    }

    private static boolean containsAny(String name, String[] keywords) {
        for (String keyword : keywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
