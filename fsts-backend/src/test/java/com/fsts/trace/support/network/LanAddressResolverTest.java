package com.fsts.trace.support.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网卡打分规则测试：排序错了就会把 VMware / WSL 的地址发给手机，扫码必然打不开。
 */
class LanAddressResolverTest {

    @Test
    void wirelessInterfaceShouldOutrankWiredAndUnknown() {
        assertTrue(LanAddressResolver.score("Intel(R) Wi-Fi 6 AX201 160MHz") > LanAddressResolver.score("Realtek PCIe GbE Family"));
        assertTrue(LanAddressResolver.score("WLAN") > LanAddressResolver.score("以太网"));
        assertTrue(LanAddressResolver.score("以太网") > LanAddressResolver.score("未知适配器"));
    }

    @Test
    void virtualInterfacesShouldRankLast() {
        assertTrue(LanAddressResolver.score("VMware Network Adapter VMnet8") < 0);
        assertTrue(LanAddressResolver.score("vEthernet (WSL)") < 0);
        assertTrue(LanAddressResolver.score("Tailscale") < 0);
        assertTrue(LanAddressResolver.score("Microsoft Wi-Fi Direct Virtual Adapter") < 0);
    }

    @Test
    void resolveReturnsOnlyPrivateIpv4Addresses() {
        List<String> hosts = LanAddressResolver.resolve();
        for (String host : hosts) {
            assertTrue(host.matches("\\d{1,3}(\\.\\d{1,3}){3}"), "非 IPv4 地址: " + host);
            assertTrue(host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("172."),
                    "非内网地址: " + host);
        }
    }
}
