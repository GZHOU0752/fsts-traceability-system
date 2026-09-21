package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批号详情中的确认请求片段（接口 10.2 {@code confirmRequest}）。
 */
@Data
@Builder
public class ConfirmRequestBriefVO {

    private String requestNo;

    private Integer requestStatus;

    private String requestStatusName;

    private LocalDateTime requestTime;

    private LocalDateTime handleTime;

    private String handleRemark;
}
