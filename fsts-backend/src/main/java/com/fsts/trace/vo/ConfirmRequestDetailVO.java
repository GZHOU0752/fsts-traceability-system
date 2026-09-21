package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 确认请求详情（接口 11.2）。
 */
@Data
@Builder
public class ConfirmRequestDetailVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String requestNo;

    private Integer requestStatus;

    private String requestStatusName;

    private LocalDateTime requestTime;

    private LocalDateTime handleTime;

    private String handleRemark;

    private String fromEnterpriseName;

    private String fromEnterpriseTypeName;

    private String fromProvinceName;

    private String fromCityName;

    private String toEnterpriseName;

    private DownstreamBatchVO downstreamBatch;

    private UpstreamBatchSimpleVO upstreamBatch;
}
