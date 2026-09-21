package com.fsts.trace.dto.stat;

import lombok.Data;

/**
 * 统计概览的数据库行（对应接口 7.1）。
 */
@Data
public class OverviewRow {

    private Long totalCount;
    private Long fishingCount;
    private Long processingCount;
    private Long wholesaleCount;
    private Long retailCount;
    private Long provinceCount;
    private Long todayRegisterCount;
}
