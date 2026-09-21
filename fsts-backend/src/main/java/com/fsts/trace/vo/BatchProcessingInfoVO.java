package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 冷冻加工环节明细（接口 10.2 {@code processingInfo}）。
 */
@Data
@Builder
public class BatchProcessingInfoVO {

    private String processForm;

    private String inspectionNo;

    private BigDecimal quickFreezeTemp;

    private BigDecimal factoryTemp;

    private String productionBatchNo;
}
