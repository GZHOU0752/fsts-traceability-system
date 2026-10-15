package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

/**
 * 批号状态流转记录（接口 10.2 {@code statusLogs} 元素）。
 */
@Data
@Builder
@Jacksonized
public class BatchStatusLogVO {

    private Integer fromStatus;

    private String fromStatusName;

    private Integer toStatus;

    private String toStatusName;

    private String action;

    private String operatorType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    private String operatorName;

    private String remark;

    private LocalDateTime createTime;
}
