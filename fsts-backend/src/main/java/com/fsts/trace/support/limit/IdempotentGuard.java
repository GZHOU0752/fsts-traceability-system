package com.fsts.trace.support.limit;

import com.fsts.trace.config.props.RedisProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 幂等守卫：拦截"同一主体 + 同一操作 + 同一请求体"的重复提交。
 *
 * <p>与数据库唯一索引的分工：
 * <ul>
 *   <li>唯一索引是<b>正确性</b>的最终保证，任何情况下都不会产生重复数据；</li>
 *   <li>本守卫是<b>性能</b>优化，把重复请求挡在数据库之前，避免无谓的事务与回滚。</li>
 * </ul>
 * 二者是互补关系而非替代关系。
 */
@Slf4j
@Component
public class IdempotentGuard {

    private static final String NAMESPACE = "idem:";

    private final RedisProperties redisProperties;
    private final StringRedisTemplate redisTemplate;
    private final Cache<String, Mark> localMarks;

    public IdempotentGuard(RedisProperties redisProperties, StringRedisTemplate redisTemplate) {
        this.redisProperties = redisProperties;
        this.redisTemplate = redisTemplate;
        // 每条占位自带 TTL（来自 @Idempotent 注解），
        // 不能用统一的 expireAfterWrite：否则注解上写的 3 秒窗口会被固定值覆盖，
        // 在未启用 Redis 的环境里退化成"几分钟内都无法重新提交"。
        this.localMarks = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfter(new Expiry<String, Mark>() {
                    @Override
                    public long expireAfterCreate(String key, Mark value, long currentTime) {
                        return value.ttl().toNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, Mark value, long currentTime, long currentDuration) {
                        return value.ttl().toNanos();
                    }

                    @Override
                    public long expireAfterRead(String key, Mark value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    /**
     * 尝试占位。
     *
     * @return true 首次请求（放行）；false 重复请求（拦截）
     */
    public boolean tryAcquire(String key, Duration ttl) {
        String redisKey = redisProperties.getKeyPrefix() + NAMESPACE + key;
        if (redisProperties.isEnabled()) {
            try {
                Boolean ok = redisTemplate.opsForValue()
                        .setIfAbsent(redisKey, "1", ttl.toMillis(), TimeUnit.MILLISECONDS);
                return Boolean.TRUE.equals(ok);
            } catch (Exception e) {
                log.warn("Redis 幂等占位失败，降级为本地判定: {}", e.getMessage());
            }
        }
        String localKey = key;
        Mark existing = localMarks.asMap().putIfAbsent(localKey, new Mark(System.currentTimeMillis(), ttl));
        return existing == null;
    }

    /**
     * 释放占位（业务处理失败时调用，允许用户立即重试）。
     */
    public void release(String key) {
        if (redisProperties.isEnabled()) {
            try {
                redisTemplate.delete(redisProperties.getKeyPrefix() + NAMESPACE + key);
                return;
            } catch (Exception e) {
                log.warn("Redis 幂等释放失败: {}", e.getMessage());
            }
        }
        localMarks.invalidate(key);
    }

    /** 暴露给运维排查用：当前本地占位数量 */
    public Set<String> localKeys() {
        return localMarks.asMap().keySet();
    }

    /** 本地占位条目：携带自身存活时长 */
    private record Mark(long createdAt, Duration ttl) {
    }
}
