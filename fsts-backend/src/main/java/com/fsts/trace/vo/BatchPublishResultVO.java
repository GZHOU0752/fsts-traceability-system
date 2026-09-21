package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发布 / 下架产品批号的响应（接口 10.6 / 10.7）。
 */
@Data
@Builder
public class BatchPublishResultVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String batchNo;

    private Integer batchStatus;

    private String batchStatusName;

    private LocalDateTime publishTime;

    private LocalDateTime offShelfTime;
}
