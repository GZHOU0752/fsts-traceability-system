package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 确认请求详情中的上游批号信息（接口 11.2 {@code upstreamBatch}）。
 */
@Data
@Builder
public class UpstreamBatchSimpleVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    private String batchNo;

    private String productVariety;

    private BigDecimal handoverTemp;

    private LocalDateTime publishTime;
}
