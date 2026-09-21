package com.fsts.trace.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 限流配置（fsts.ratelimit.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fsts.ratelimit")
public class RateLimitProperties {

    /** 消费者端溯源查询：每秒放行数 */
    private int publicQps = 200;

    /** 消费者端溯源查询：突发桶容量 */
    private int publicBurst = 400;

    /** 登录接口：每秒放行数（防撞库） */
    private int loginQps = 20;

    /** 登录接口：突发桶容量 */
    private int loginBurst = 40;
}
