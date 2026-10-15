package com.fsts.trace.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 缓存配置（fsts.cache.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fsts.cache")
public class CacheProperties {

    /** 本地缓存最大条目数 */
    private Long localMaxSize = 10000L;

    /** 字典缓存时长（秒） */
    private Long dictTtlSeconds = 1800L;

    /** 区划缓存时长（秒） */
    private Long regionTtlSeconds = 86400L;

    /**
     * 大屏聚合结果缓存时长（秒），默认 30；设为 0 关闭。
     * 企业数据变更时会主动失效，因此该 TTL 只影响"没有写操作时"的重复聚合查询。
     */
    private int statisticsTtlSeconds = 30;
}
