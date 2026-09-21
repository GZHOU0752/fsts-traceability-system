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
 * 冷冻加工环节明细（batch_processing），与主表一一对应（uk_processing_batch）。
 */
@Data
@TableName("batch_processing")
public class BatchProcessing implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    /** 加工形态（中文名称，候选值来自字典 process_form） */
    private String processForm;

    private String inspectionNo;

    /** 速冻中心温度（℃），阈值 -35 */
    private BigDecimal quickFreezeTemp;

    /** 出厂温度（℃），阈值 -18，同时作为本环节交接温度 */
    private BigDecimal factoryTemp;

    private String productionBatchNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
