package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

/**
 * 拒绝进场结果（接口 11.4）。
 */
@Data
@Builder
public class RejectResultVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    private Integer requestStatus;

    private String requestStatusName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    private Integer batchStatus;

    private String batchStatusName;
}
