package com.fsts.trace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 下游企业进场确认请求（batch_confirm_request）。
 *
 * <p>{@code uk_request_batch} 保证"同一批号同时只允许存在一条有效请求"，
 * 该唯一索引是并发重复提交的最终防线（重复发送时更新同一条记录而非新增）。
 */
@Data
@TableName("batch_confirm_request")
public class BatchConfirmRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String requestNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    private String batchNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long upstreamBatchId;

    private String upstreamBatchNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromEnterpriseId;

    private String fromEnterpriseName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long toEnterpriseId;

    private String toEnterpriseName;

    private BigDecimal handoverTemp;

    /** 1 待确认，2 已确认，3 已拒绝，4 已撤回 */
    private Integer requestStatus;

    private LocalDateTime requestTime;

    private LocalDateTime handleTime;

    private String handleRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
