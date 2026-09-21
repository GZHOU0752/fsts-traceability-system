package com.fsts.trace.config;

import com.fsts.trace.web.AuthInterceptor;
import com.fsts.trace.web.CurrentUserArgumentResolver;
import com.fsts.trace.web.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web 层配置：拦截器注册顺序、CORS、自定义参数解析器。
 *
 * <p>拦截器顺序很重要：<b>先限流、后鉴权</b>。
 * 若顺序颠倒，攻击者可以用海量无令牌请求打满鉴权逻辑（含 JWT 验签的 CPU 开销），
 * 限流就失去了"最外层护城河"的意义。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 免鉴权路径：登录接口本身不能要求登录 */
    private static final String[] AUTH_WHITELIST = {
            "/api/admin/auth/login",
            "/api/enterprise/auth/login",
            "/error"
    };

    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebMvcConfig(AuthInterceptor authInterceptor,
                        RateLimitInterceptor rateLimitInterceptor,
                        CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.authInterceptor = authInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 第一道：限流（仅覆盖免登录接口与登录接口）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/public/**", "/api/admin/auth/login", "/api/enterprise/auth/login")
                .order(1);

        // 第二道：鉴权（管理端与企业端全量覆盖）
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/admin/**", "/api/enterprise/**")
                .excludePathPatterns(AUTH_WHITELIST)
                .order(2);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    /**
     * CORS 配置。
     *
     * <p>开发期前端通过 vue.config.js 的 devServer.proxy 同源转发，本不需要 CORS；
     * 这里放开是为了支持"前端独立域名部署"的场景。
     * 生产环境请把 allowedOriginPatterns 收窄为实际域名，并关闭 allowCredentials。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Trace-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
