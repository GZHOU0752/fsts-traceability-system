package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

/**
 * 大屏业务指标卡片（接口 7.6）。
 *
 * <p>与 7.1 的企业维度概览互补：7.1 回答"平台上有多少家企业"，
 * 这里回答"平台上正在发生什么"——有多少批次在架、有多少批次冷链异常、
 * 有多少确认请求积压。后者才是运营视角下需要盯的数字。
 */
@Data
@Builder
@Jacksonized
public class BusinessMetricsVO {

    private Long totalBatchCount;

    private Long activeBatchCount;

    private Long coldChainAbnormalCount;

    private Long pendingConfirmCount;

    private Long validTraceCodeCount;

    private LocalDateTime updateTime;
}
