package com.fsts.trace.support.cache;

import java.time.Duration;
import java.util.List;

/**
 * 二级缓存抽象。
 *
 * <p>存在两套实现，通过 {@code fsts.redis.enabled} 切换：
 * <ul>
 *   <li>{@link LocalCacheService}：单实例 Caffeine，默认实现，零外部依赖；</li>
 *   <li>{@link RedisCacheService}：Redis 共享缓存，多实例部署时保证缓存一致。</li>
 * </ul>
 *
 * <p>内部统一以 JSON 字符串存放，避免"本地缓存返回共享可变对象"带来的并发隐患，
 * 同时让 Redis 与本地方案在行为上完全对齐（可随时切换、可平滑降级）。
 */
public interface CacheService {

    /**
     * 读取单个对象。
     *
     * @return 未命中返回 null
     */
    <T> T get(String namespace, String key, Class<T> type);

    /**
     * 读取列表对象。
     */
    <T> List<T> getList(String namespace, String key, Class<T> elementType);

    /**
     * 写入缓存。
     */
    void put(String namespace, String key, Object value, Duration ttl);

    /**
     * 删除单个键。
     */
    void evict(String namespace, String key);

    /**
     * 清空整个命名空间。
     */
    void evictNamespace(String namespace);
}
