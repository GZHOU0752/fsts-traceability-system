package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 零售环节明细（接口 10.2 {@code retailInfo}）。
 */
@Data
@Builder
public class BatchRetailInfoVO {

    private LocalDate shelfDate;

    private BigDecimal displayTemp;

    private String saleStore;

    private String displayEquipNo;
}
