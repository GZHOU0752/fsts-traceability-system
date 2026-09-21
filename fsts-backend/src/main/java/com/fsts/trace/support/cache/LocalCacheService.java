package com.fsts.trace.support.cache;

import com.fsts.trace.common.util.JsonUtils;
import com.fsts.trace.config.props.CacheProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 本地缓存实现（Caffeine），Redis 未启用时的默认方案。
 *
 * <p>每条缓存自带 TTL（通过 Caffeine {@link Expiry} 策略实现），
 * 因此字典、区划等不同生命周期的数据可以共用同一个缓存实例。
 * 命中时反序列化为新对象，杜绝调用方修改缓存内容导致的"脏读扩散"。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fsts.redis.enabled", havingValue = "false", matchIfMissing = true)
public class LocalCacheService implements CacheService {

    private final Cache<String, Entry> cache;

    public LocalCacheService(CacheProperties properties) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getLocalMaxSize())
                .expireAfter(new Expiry<String, Entry>() {
                    @Override
                    public long expireAfterCreate(String key, Entry value, long currentTime) {
                        return value.ttl().toNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, Entry value, long currentTime, long currentDuration) {
                        return value.ttl().toNanos();
                    }

                    @Override
                    public long expireAfterRead(String key, Entry value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
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
        cache.put(buildKey(namespace, key), new Entry(JsonUtils.toJson(value), ttl));
    }

    @Override
    public void evict(String namespace, String key) {
        cache.invalidate(buildKey(namespace, key));
    }

    @Override
    public void evictNamespace(String namespace) {
        String prefix = namespace + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        log.info("本地缓存命名空间已清空: {}", namespace);
    }

    private String read(String namespace, String key) {
        Entry entry = cache.getIfPresent(buildKey(namespace, key));
        return entry == null ? null : entry.json();
    }

    private String buildKey(String namespace, String key) {
        return namespace + ":" + key;
    }

    /** 缓存条目：值 + 该条目的存活时长 */
    private record Entry(String json, Duration ttl) {
    }
}
