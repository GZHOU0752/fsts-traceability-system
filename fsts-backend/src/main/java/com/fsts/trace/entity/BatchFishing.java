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
 * 捕捞与养殖环节明细（batch_fishing），与主表一一对应（uk_fishing_batch）。
 */
@Data
@TableName("batch_fishing")
public class BatchFishing implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    private LocalDate catchBreedDate;

    /** 1 产地检疫合格证明，2 渔获物上岸证明 */
    private Integer certificateType;

    private String certificateNo;

    /** 起运温度（℃），阈值 -18 */
    private BigDecimal departureTemp;

    private String drugReportNo;

    private String fishingLogNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
