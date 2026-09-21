package com.fsts.trace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置。
 *
 * <p>选用 BCrypt 的原因：自带随机盐、计算代价可调，
 * 即便数据库泄露也难以通过彩虹表反推明文。
 * 代价因子保持默认 10：单次校验约 50~80ms，既能抵抗暴力破解，
 * 又不会在登录高峰成为 CPU 瓶颈（登录接口已单独限流）。
 */
@Configuration
public class SecurityBeanConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
