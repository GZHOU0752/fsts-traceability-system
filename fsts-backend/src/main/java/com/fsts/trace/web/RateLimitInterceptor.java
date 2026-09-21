package com.fsts.trace.web;

import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.ErrorCode;
import com.fsts.trace.common.util.ClientIpUtils;
import com.fsts.trace.config.props.RateLimitProperties;
import com.fsts.trace.support.limit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 限流拦截器：保护免登录接口与登录接口。
 *
 * <p>为什么只拦这两类：
 * 管理端与企业端已通过 JWT 鉴权，恶意流量在鉴权层就被挡掉；
 * 而 {@code /api/public/**} 与登录接口免鉴权，是唯一能"零成本"消耗服务端资源的入口。
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String PUBLIC_PREFIX = "/api/public/";
    private static final String LOGIN_SUFFIX = "/auth/login";

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    public RateLimitInterceptor(RateLimiter rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        String ip = ClientIpUtils.resolve(request);

        boolean allowed;
        if (uri.endsWith(LOGIN_SUFFIX)) {
            allowed = rateLimiter.tryAcquire("login:" + ip, properties.getLoginQps(), properties.getLoginBurst());
        } else if (uri.startsWith(PUBLIC_PREFIX)) {
            allowed = rateLimiter.tryAcquire("public:" + ip, properties.getPublicQps(), properties.getPublicBurst());
        } else {
            return true;
        }

        if (!allowed) {
            log.warn("触发限流 uri={} ip={}", uri, ip);
            throw BusinessException.of(ErrorCode.TOO_MANY_REQUESTS);
        }
        return true;
    }
}
