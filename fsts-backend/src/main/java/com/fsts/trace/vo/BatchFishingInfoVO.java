package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 捕捞与养殖环节明细（接口 10.2 {@code fishingInfo}）。
 */
@Data
@Builder
public class BatchFishingInfoVO {

    private LocalDate catchBreedDate;

    private Integer certificateType;

    private String certificateTypeName;

    private String certificateNo;

    private BigDecimal departureTemp;

    private String drugReportNo;

    private String fishingLogNo;
}
