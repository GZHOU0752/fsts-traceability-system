package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批号详情中的确认请求片段（接口 10.2 {@code confirmRequest}）。
 */
@Data
@Builder
public class ConfirmRequestBriefVO {

    /**
     * 请求主键。
     *
     * <p>下游企业要"撤回"自己发起的请求（接口 11.5）时必须拿到请求 ID，
     * 而批号详情是它唯一能看到该请求的入口（11.1 只返回接收方视角的列表），
     * 因此这里必须下发主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    private String requestNo;

    private Integer requestStatus;

    private String requestStatusName;

    private LocalDateTime requestTime;

    private LocalDateTime handleTime;

    private String handleRemark;
}
