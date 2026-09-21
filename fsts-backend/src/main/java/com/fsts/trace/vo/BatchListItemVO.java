package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 本企业批号列表项（接口 10.1）。
 */
@Data
@Builder
public class BatchListItemVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String batchNo;

    private String productVariety;

    private String upstreamBatchNo;

    private String upstreamEnterpriseName;

    private Integer batchStatus;

    private String batchStatusName;

    private BigDecimal handoverTemp;

    private Boolean coldChainOk;

    private LocalDateTime createTime;
}
