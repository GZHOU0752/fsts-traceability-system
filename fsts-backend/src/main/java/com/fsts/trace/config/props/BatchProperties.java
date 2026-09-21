package com.fsts.trace.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 批号相关配置（fsts.batch.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fsts.batch")
public class BatchProperties {

    /** 溯源码生成冲突时的最大重试次数（唯一索引兜底） */
    private int traceCodeMaxRetry = 8;
}
