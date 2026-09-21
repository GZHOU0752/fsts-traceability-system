package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 确认请求详情中的下游批号信息（接口 11.2 {@code downstreamBatch}）。
 *
 * <p>温度字段按下游企业类型选择性填充（加工取速冻/出厂温度，批发取三温，零售取陈列温度）。
 */
@Data
@Builder
public class DownstreamBatchVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    private String batchNo;

    private String productVariety;

    private LocalDate wholesaleDate;

    private BigDecimal inboundTemp;

    private BigDecimal coldStorageTemp;

    private BigDecimal outboundTemp;

    private BigDecimal quickFreezeTemp;

    private BigDecimal factoryTemp;

    private BigDecimal displayTemp;

    private BigDecimal handoverTemp;

    private Boolean coldChainOk;

    private String saleStore;
}
