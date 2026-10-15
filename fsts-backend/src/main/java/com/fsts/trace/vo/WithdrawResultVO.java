package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 撤回进场确认请求结果（接口 11.5）。
 */
@Data
@Builder
public class WithdrawResultVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    private String requestNo;

    private Integer requestStatus;

    private String requestStatusName;

    private LocalDateTime handleTime;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    private String batchNo;

    private Integer batchStatus;

    private String batchStatusName;
}
