package com.fsts.trace.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户端 IP 解析回归测试。
 *
 * <p>重点守住一条安全边界：转发头是客户端可伪造的，只有在直连对端本身
 * 可信（内网 / 回环）时才允许采信，否则按 IP 限流可以被一行请求头绕过。
 */
class ClientIpUtilsTest {

    @Test
    void publicCallerCannotSpoofForwardedHeaderToFakeRateLimitKey() {
        MockHttpServletRequest request = request("203.0.113.7");
        request.addHeader("X-Forwarded-For", "10.0.0.9");
        request.addHeader("X-Real-IP", "10.0.0.9");

        assertEquals("203.0.113.7", ClientIpUtils.resolve(request),
                "公网直连时必须忽略伪造的转发头，否则限流可被轻易绕过");
    }

    @Test
    void reverseProxyKeepsRealClientButDropsClientForgedPrefix() {
        MockHttpServletRequest request = request("127.0.0.1");
        // 第一个条目是客户端自己伪造的，第二个才是 Nginx 追加的真实来源
        request.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.7");

        assertEquals("203.0.113.7", ClientIpUtils.resolve(request),
                "应取最右侧的不可信地址，客户端伪造的前缀必须被忽略");
    }

    @Test
    void fallsBackToRemoteAddrWhenAllHopsAreTrusted() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "192.168.1.30");

        assertEquals("127.0.0.1", ClientIpUtils.resolve(request));
    }

    @Test
    void ignoresMalformedForwardedValues() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "unknown, not-an-ip");
        request.addHeader("X-Real-IP", "203.0.113.9");

        assertEquals("203.0.113.9", ClientIpUtils.resolve(request));
    }

    @Test
    void stripsPortAndBrackets() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7:58234");

        assertEquals("203.0.113.7", ClientIpUtils.resolve(request));

        MockHttpServletRequest ipv6 = request("127.0.0.1");
        ipv6.addHeader("X-Real-IP", "[2001:db8::5]");
        assertEquals("2001:db8::5", ClientIpUtils.resolve(ipv6));
    }

    @Test
    void recognisesPrivateAndPublicRanges() {
        assertTrue(ClientIpUtils.isTrustedPeer("127.0.0.1"));
        assertTrue(ClientIpUtils.isTrustedPeer("10.1.2.3"));
        assertTrue(ClientIpUtils.isTrustedPeer("172.20.5.6"));
        assertTrue(ClientIpUtils.isTrustedPeer("192.168.31.7"));
        assertTrue(ClientIpUtils.isTrustedPeer("169.254.1.1"));
        assertTrue(ClientIpUtils.isTrustedPeer("::1"));
        assertTrue(ClientIpUtils.isTrustedPeer("fd00::1"));

        assertFalse(ClientIpUtils.isTrustedPeer("172.32.0.1"), "172.32 不在 172.16/12 内");
        assertFalse(ClientIpUtils.isTrustedPeer("203.0.113.7"));
        assertFalse(ClientIpUtils.isTrustedPeer("2001:db8::1"));
        assertFalse(ClientIpUtils.isTrustedPeer("999.1.1.1"), "超范围的四段数字不是合法 IPv4");
        assertFalse(ClientIpUtils.isTrustedPeer(null));
        assertFalse(ClientIpUtils.isTrustedPeer("unknown"));
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
