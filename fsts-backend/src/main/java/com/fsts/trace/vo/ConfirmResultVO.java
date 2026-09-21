package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 确认进场结果（接口 11.3）。
 */
@Data
@Builder
public class ConfirmResultVO {

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

    /** 零售商批号确认时同步生成的标识码 */
    private TraceCodeInfoVO traceCode;
}
