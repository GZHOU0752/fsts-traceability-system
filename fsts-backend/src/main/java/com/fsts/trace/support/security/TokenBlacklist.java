package com.fsts.trace.support.security;

import com.fsts.trace.config.props.JwtProperties;
import com.fsts.trace.config.props.RedisProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 令牌黑名单：解决"无状态 JWT 无法主动失效"的问题（退出登录、改密码后旧令牌立即作废）。
 *
 * <p>存储策略由 {@code fsts.redis.enabled} 决定：
 * <ul>
 *   <li>启用 Redis：多实例共享黑名单，任意实例退出后其他实例立即拒绝该令牌；</li>
 *   <li>未启用：退化为单实例本地黑名单（本地开发足够；集群环境应务必启用 Redis）。</li>
 * </ul>
 *
 * <p><b>失效时的取舍</b>：查询黑名单失败（Redis 宕机）时按"放行"处理（fail-open）。
 * 理由：令牌本身仍有最长 2 小时的硬过期时间，而 fail-closed 会把"Redis 抖动"
 * 放大成"全站无法登录"，可用性损失远大于风险收益。
 */
@Slf4j
@Component
public class TokenBlacklist {

    private static final String NAMESPACE = "jwt:blacklist:";

    private final RedisProperties redisProperties;
    private final StringRedisTemplate redisTemplate;
    private final Cache<String, Boolean> localCache;

    public TokenBlacklist(RedisProperties redisProperties, StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisProperties = redisProperties;
        this.redisTemplate = redisTemplate;
        // 本地兜底缓存：存活时间取 Token 最大有效期，到期自动清理，避免内存无限增长
        long maxTtlSeconds = Math.max(jwtProperties.getExpireSeconds(), 60L);
        this.localCache = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfter(new Expiry<String, Boolean>() {
                    @Override
                    public long expireAfterCreate(String key, Boolean value, long currentTime) {
                        return Duration.ofSeconds(maxTtlSeconds).toNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, Boolean value, long currentTime, long currentDuration) {
                        return Duration.ofSeconds(maxTtlSeconds).toNanos();
                    }

                    @Override
                    public long expireAfterRead(String key, Boolean value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    /**
     * 将令牌加入黑名单，存活时长等于其剩余有效期。
     */
    public void add(String tokenId, long ttlSeconds) {
        if (tokenId == null || tokenId.isEmpty() || ttlSeconds <= 0) {
            return;
        }
        if (redisProperties.isEnabled()) {
            try {
                redisTemplate.opsForValue().set(key(tokenId), "1", ttlSeconds, TimeUnit.SECONDS);
                return;
            } catch (Exception e) {
                log.warn("Redis 写入令牌黑名单失败，降级为本地黑名单: {}", e.getMessage());
            }
        }
        localCache.put(tokenId, Boolean.TRUE);
        // 本地缓存无法按条目设置 TTL，统一按 Token 最大有效期过期
        log.debug("令牌已加入本地黑名单: {}", tokenId);
    }

    /**
     * 判断令牌是否已被拉黑。
     */
    public boolean contains(String tokenId) {
        if (tokenId == null || tokenId.isEmpty()) {
            return false;
        }
        if (redisProperties.isEnabled()) {
            try {
                return Boolean.TRUE.equals(redisTemplate.hasKey(key(tokenId)));
            } catch (Exception e) {
                log.warn("Redis 查询令牌黑名单失败，按放行处理(fail-open): {}", e.getMessage());
                return false;
            }
        }
        return localCache.getIfPresent(tokenId) != null;
    }

    private String key(String tokenId) {
        return redisProperties.getKeyPrefix() + NAMESPACE + tokenId;
    }
}
