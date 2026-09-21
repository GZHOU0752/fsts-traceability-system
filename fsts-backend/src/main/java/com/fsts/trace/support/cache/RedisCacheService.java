package com.fsts.trace.support.cache;

import com.fsts.trace.common.util.JsonUtils;
import com.fsts.trace.config.props.RedisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Redis 二级缓存实现，多实例部署时启用。
 *
 * <p>可用性设计：所有 Redis 操作都包在 try/catch 中，
 * 一旦 Redis 抖动/宕机，读操作按"未命中"处理、写操作静默降级，
 * 保证业务退化为"直连数据库"，而不是整体不可用。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fsts.redis.enabled", havingValue = "true")
public class RedisCacheService implements CacheService {

    private static final String SEPARATOR = ":";

    private final StringRedisTemplate redis;
    private final RedisProperties properties;

    public RedisCacheService(StringRedisTemplate redis, RedisProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public <T> T get(String namespace, String key, Class<T> type) {
        String json = read(namespace, key);
        return json == null ? null : JsonUtils.parse(json, type);
    }

    @Override
    public <T> List<T> getList(String namespace, String key, Class<T> elementType) {
        String json = read(namespace, key);
        if (json == null) {
            return null;
        }
        return JsonUtils.parseList(json, elementType);
    }

    @Override
    public void put(String namespace, String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(buildKey(namespace, key), JsonUtils.toJson(value), ttl);
        } catch (Exception e) {
            log.warn("Redis 写入失败，已降级为直接查库: namespace={} key={} err={}", namespace, key, e.getMessage());
        }
    }

    @Override
    public void evict(String namespace, String key) {
        try {
            redis.delete(buildKey(namespace, key));
        } catch (Exception e) {
            log.warn("Redis 删除失败: namespace={} key={} err={}", namespace, key, e.getMessage());
        }
    }

    @Override
    public void evictNamespace(String namespace) {
        String pattern = properties.getKeyPrefix() + namespace + SEPARATOR + "*";
        try {
            Set<String> keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
            log.info("Redis 缓存命名空间已清空: {} ({} 个键)", namespace, keys == null ? 0 : keys.size());
        } catch (Exception e) {
            log.warn("Redis 清空命名空间失败: namespace={} err={}", namespace, e.getMessage());
        }
    }

    private String read(String namespace, String key) {
        try {
            return redis.opsForValue().get(buildKey(namespace, key));
        } catch (Exception e) {
            log.warn("Redis 读取失败，按未命中处理: namespace={} key={} err={}", namespace, key, e.getMessage());
            return null;
        }
    }

    private String buildKey(String namespace, String key) {
        return properties.getKeyPrefix() + namespace + SEPARATOR + key;
    }
}
