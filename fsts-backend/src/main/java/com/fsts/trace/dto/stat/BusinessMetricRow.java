package com.fsts.trace.dto.stat;

import lombok.Data;

/**
 * 大屏业务指标聚合行（接口 7.6）。
 *
 * <p>一条 SQL 同时算出批号与确认请求维度的指标：这几张表都很小，
 * 合并成一条语句比拆成 5 次往返更省连接。
 */
@Data
public class BusinessMetricRow {

    /** 批号总数 */
    private Long totalBatchCount;

    /** 在架批号数（已确认/已发布，可被下游选择） */
    private Long activeBatchCount;

    /** 冷链异常批号数（cold_chain_ok = 0） */
    private Long coldChainAbnormalCount;

    /** 待处理确认请求数（request_status = 1） */
    private Long pendingConfirmCount;

    /** 有效溯源标识码数（status = 1） */
    private Long validTraceCodeCount;
}
