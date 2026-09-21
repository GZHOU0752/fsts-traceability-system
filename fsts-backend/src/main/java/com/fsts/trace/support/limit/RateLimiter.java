package com.fsts.trace.support.limit;

import com.fsts.trace.config.props.RedisProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 限流器：消费者端与登录接口的流量保护。
 *
 * <p>两套实现自动切换：
 * <ul>
 *   <li>Redis 模式：Lua 脚本实现固定窗口计数，多实例共享配额（真正意义上的全局限流）；</li>
 *   <li>本地模式：令牌桶，单实例限流，无外部依赖。</li>
 * </ul>
 *
 * <p>为什么消费者端必须限流：{@code /api/public/**} 免登录，是最容易被脚本刷的入口，
 * 一旦被刷会迅速占满数据库连接池，进而拖垮企业端与管理端（级联故障）。
 */
@Slf4j
@Component
public class RateLimiter {

    /**
     * 固定窗口计数脚本：INCR 后首次设置过期时间，整体原子执行。
     * 返回当前窗口内的请求数，调用方与阈值比较。
     */
    private static final String LUA_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """;

    private static final DefaultRedisScript<Long> SCRIPT =
            new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    private final RedisProperties redisProperties;
    private final StringRedisTemplate redisTemplate;
    private final Cache<String, TokenBucket> localBuckets;

    public RateLimiter(RedisProperties redisProperties, StringRedisTemplate redisTemplate) {
        this.redisProperties = redisProperties;
        this.redisTemplate = redisTemplate;
        // 桶本身很轻量，过期后自动释放；上限防止被海量随机 key 撑爆
        this.localBuckets = Caffeine.newBuilder()
                .maximumSize(200_000)
                .expireAfterAccess(Duration.ofMinutes(10))
                .build();
    }

    /**
     * 尝试获取一个令牌。
     *
     * @param key   限流键（如 public:trace:ip）
     * @param qps   每秒放行数
     * @param burst 突发容量
     * @return true 放行，false 触发限流
     */
    public boolean tryAcquire(String key, int qps, int burst) {
        if (qps <= 0) {
            return true;
        }
        int capacity = Math.max(burst, qps);
        if (redisProperties.isEnabled()) {
            return tryAcquireByRedis(key, qps, capacity);
        }
        return tryAcquireByLocalBucket(key, qps, capacity);
    }

    /**
     * Redis 固定窗口计数：实现简单、无状态、多实例一致。
     * 极端情况下窗口边界可能瞬时放行 2 倍流量，对本系统（读多写少）完全可接受。
     */
    private boolean tryAcquireByRedis(String key, int qps, int burst) {
        String redisKey = redisProperties.getKeyPrefix() + "limit:" + key;
        try {
            Long current = redisTemplate.execute(SCRIPT, Collections.singletonList(redisKey),
                    String.valueOf(TimeUnit.SECONDS.toMillis(1)));
            return current != null && current <= burst;
        } catch (Exception e) {
            log.warn("Redis 限流失败，降级为本地令牌桶: {}", e.getMessage());
            return tryAcquireByLocalBucket(key, qps, burst);
        }
    }

    /**
     * 本地令牌桶：按时间匀速补充令牌，允许一定突发。
     */
    private boolean tryAcquireByLocalBucket(String key, int qps, int capacity) {
        TokenBucket bucket = localBuckets.get(key, k -> new TokenBucket(capacity, qps));
        return bucket.tryAcquire();
    }

    /**
     * 令牌桶实现（单个桶内 synchronized，粒度足够细，不构成全局竞争点）。
     */
    private static final class TokenBucket {

        private final double capacity;
        private final double refillPerNano;

        private double tokens;
        private long lastRefillNanos;

        TokenBucket(int capacity, int qps) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.refillPerNano = qps / 1_000_000_000.0d;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            double refill = (now - lastRefillNanos) * refillPerNano;
            if (refill > 0) {
                tokens = Math.min(capacity, tokens + refill);
                lastRefillNanos = now;
            }
            if (tokens >= 1.0d) {
                tokens -= 1.0d;
                return true;
            }
            return false;
        }
    }
}
