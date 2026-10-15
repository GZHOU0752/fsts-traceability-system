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
 * 产品批号状态流转日志（batch_status_log）。
 *
 * <p>存在的意义：批号主表只保留"当前状态"，回答不了"谁在什么时候把它发布/下架了"。
 * 溯源系统一旦出现争议（例如某批次被提前下架导致消费者查不到），
 * 没有操作主体与时间的记录就无法复盘，这也是审计要求里最常被问到的一条。
 */
@Data
@TableName("batch_status_log")
public class BatchStatusLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    private String batchNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long enterpriseId;

    private String enterpriseName;

    /** 变更前状态，新建时为 null */
    private Integer fromStatus;

    /** 变更后状态 */
    private Integer toStatus;

    /** 动作名称：新建 / 发布 / 发送确认请求 / 确认进场 / 拒绝进场 / 撤回请求 / 下架 / 恢复上架 */
    private String action;

    /** 操作主体类型：ADMIN / ENTERPRISE / SYSTEM */
    private String operatorType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    private String operatorName;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
