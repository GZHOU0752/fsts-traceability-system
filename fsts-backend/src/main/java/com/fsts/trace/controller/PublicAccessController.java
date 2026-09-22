package com.fsts.trace.controller;

import com.fsts.trace.common.Result;
import com.fsts.trace.common.util.ClientIpUtils;
import com.fsts.trace.support.network.LanAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * 局域网访问辅助接口（扫码溯源配套能力）。
 *
 * <p>二维码里承载的是追溯页链接，手机扫码后由手机浏览器打开，所以链接必须是
 * 内网可访问的地址。开发联调时前端运行在 localhost，页面自己拿不到本机内网 IP，
 * 因此由服务端探测后下发，前端再把二维码内容改写成
 * {@code http://<内网IP>:<前端端口>/trace/<溯源码>}。
 *
 * <p>安全约束：仅当调用方本身就来自内网（回环或私有地址）时才返回结果，
 * 避免公网请求借此探测服务器内网拓扑；接口位于 {@code /api/public/**}，
 * 与消费者溯源接口共用同一套限流。
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicAccessController {

    /**
     * 5.4 查询可用于局域网访问的主机地址。
     */
    @GetMapping("/access-hosts")
    public Result<List<String>> accessHosts(HttpServletRequest request) {
        String clientIp = ClientIpUtils.resolve(request);
        if (!isPrivateAddress(clientIp)) {
            return Result.ok("非内网调用，不返回局域网地址", List.of());
        }
        return Result.ok("查询成功", LanAddressResolver.resolve());
    }

    private static boolean isPrivateAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || isUniqueLocalIpv6(address);
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** IPv6 唯一本地地址（fc00::/7） */
    private static boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }
}
