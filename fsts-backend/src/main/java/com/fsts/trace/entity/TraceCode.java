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
import java.time.LocalDateTime;

/**
 * 溯源标识码（trace_code）：消费者端唯一查询入口。
 *
 * <p>{@code uk_trace_code} 全局唯一、{@code uk_trace_batch} 一批一码，
 * 二者是溯源码生成并发安全的核心保障。
 */
@Data
@TableName("trace_code")
public class TraceCode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String traceCode;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    private String batchNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long retailerId;

    private String retailerName;

    private String productVariety;

    private String saleStore;

    private String qrContent;

    /** 1 有效，0 失效 */
    private Integer status;

    private Integer queryCount;

    private LocalDateTime lastQueryTime;

    private LocalDateTime generateTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
