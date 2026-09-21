package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 批发与冷链储运环节明细（接口 10.2 {@code wholesaleInfo}）。
 */
@Data
@Builder
public class BatchWholesaleInfoVO {

    private LocalDate wholesaleDate;

    private BigDecimal inboundTemp;

    private BigDecimal coldStorageTemp;

    private BigDecimal outboundTemp;

    private String transportToolNo;

    private String coldStorageNo;
}
