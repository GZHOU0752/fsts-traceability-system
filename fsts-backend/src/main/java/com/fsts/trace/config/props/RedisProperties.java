package com.fsts.trace.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Redis 开关与键前缀（fsts.redis.*）。
 *
 * <p>Redis 在本系统中属于"增强项"而非"必需项"：
 * 关闭或不可用时，缓存、锁、限流、黑名单全部自动降级为本地实现，主流程不受影响。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fsts.redis")
public class RedisProperties {

    /** 是否启用 Redis 二级能力 */
    private boolean enabled = false;

    /** 全局键前缀，便于同实例多系统共用时隔离 */
    private String keyPrefix = "fsts:";
}
