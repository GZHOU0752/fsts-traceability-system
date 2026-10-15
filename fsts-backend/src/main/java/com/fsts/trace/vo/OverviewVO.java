package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

/**
 * 大屏概览卡片（接口 7.1）。
 */
@Data
@Builder
@Jacksonized
public class OverviewVO {

    private Long totalCount;

    private Long fishingCount;

    private Long processingCount;

    private Long wholesaleCount;

    private Long retailCount;

    private Long provinceCount;

    private Long todayRegisterCount;

    private LocalDateTime updateTime;
}
