package com.fsts.trace.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 溯源相关配置（fsts.trace.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fsts.trace")
public class TraceProperties {

    /** 二维码内容前缀，最终拼接为 {qrBaseUrl}{traceCode} */
    private String qrBaseUrl = "https://fsts.example.com/trace/";

    /** 查询次数聚合落库间隔（毫秒） */
    private long queryCountFlushIntervalMs = 5000L;

    /** 单次聚合落库的最大条数 */
    private int queryCountFlushBatch = 500;
}
