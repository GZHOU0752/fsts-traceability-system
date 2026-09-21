package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 上游企业已发布批号（接口 9.2）。
 *
 * <p>{@code productVariety} 与 {@code sourceType} 即"上游带出"数据项。
 */
@Data
@Builder
public class UpstreamBatchVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String batchNo;

    private String productVariety;

    private Integer sourceType;

    private String sourceTypeName;

    private String enterpriseName;

    private LocalDateTime publishTime;

    private BigDecimal handoverTemp;

    private Boolean coldChainOk;
}
