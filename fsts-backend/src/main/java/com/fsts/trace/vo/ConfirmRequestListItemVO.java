package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 待处理进场确认请求列表项（接口 11.1）。
 *
 * <p>该 VO 由 Mapper 直接映射（join 企业表补充下游企业类型），因此使用可变对象。
 */
@Data
public class ConfirmRequestListItemVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String requestNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    private String batchNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long upstreamBatchId;

    private String upstreamBatchNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromEnterpriseId;

    private String fromEnterpriseName;

    private Integer fromEnterpriseType;

    private String fromEnterpriseTypeName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long toEnterpriseId;

    private String toEnterpriseName;

    private BigDecimal handoverTemp;

    private Integer requestStatus;

    private String requestStatusName;

    private LocalDateTime requestTime;
}
