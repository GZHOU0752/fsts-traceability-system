package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新建 / 更新产品批号的响应（接口 10.3 / 10.4）。
 */
@Data
@Builder
public class BatchSaveResultVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String batchNo;

    private Integer batchStatus;

    private String batchStatusName;

    private BigDecimal handoverTemp;

    private Boolean coldChainOk;

    /** 同时发送确认请求时返回请求单号，否则为 null */
    private String confirmRequestNo;

    /** 仅零售商批号在状态 3 时返回 */
    private TraceCodeInfoVO traceCode;
}
