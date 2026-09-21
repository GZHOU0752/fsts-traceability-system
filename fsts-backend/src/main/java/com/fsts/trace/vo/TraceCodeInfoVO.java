package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批号详情中的溯源标识码片段（接口 10.2 {@code traceCode}）。
 */
@Data
@Builder
public class TraceCodeInfoVO {

    private String traceCode;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    private String batchNo;

    private String productVariety;

    private String saleStore;

    private String qrContent;

    private Integer status;

    private String statusName;

    private LocalDateTime generateTime;

    private Integer queryCount;

    private LocalDateTime lastQueryTime;
}
