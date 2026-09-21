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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 批发与冷链储运环节明细（batch_wholesale），与主表一一对应（uk_wholesale_batch）。
 */
@Data
@TableName("batch_wholesale")
public class BatchWholesale implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    private LocalDate wholesaleDate;

    /** 入库温度（℃），阈值 -18 */
    private BigDecimal inboundTemp;

    /** 冷库温度（℃），阈值 -18 */
    private BigDecimal coldStorageTemp;

    /** 出库温度（℃），阈值 -18，同时作为本环节交接温度 */
    private BigDecimal outboundTemp;

    private String transportToolNo;

    private String coldStorageNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
