package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发送进场确认请求的响应（接口 10.9）。
 */
@Data
@Builder
public class ConfirmRequestSendVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    private String requestNo;

    private Integer requestStatus;

    private String requestStatusName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    private String batchNo;

    private Integer batchStatus;

    private String batchStatusName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long upstreamEnterpriseId;

    private String upstreamEnterpriseName;

    private String upstreamBatchNo;

    private BigDecimal handoverTemp;

    private LocalDateTime requestTime;
}
