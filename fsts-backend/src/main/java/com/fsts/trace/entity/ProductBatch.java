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
 * 产品批号主表（product_batch）：四个流通环节共有的数据项。
 *
 * <p>注意：本实体 <b>不加</b> {@code @TableLogic}。接口文档 10.5 规定删除采用物理删除，
 * 由外键 ON DELETE CASCADE 同步清理明细、确认请求与溯源码。
 */
@Data
@TableName("product_batch")
public class ProductBatch implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String batchNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long enterpriseId;

    private String enterpriseName;

    /** 所属环节：1 捕捞与养殖，2 冷冻加工，3 批发，4 零售 */
    private Integer enterpriseType;

    // ---- 上游带出（源头环节为 null）----
    @JsonSerialize(using = ToStringSerializer.class)
    private Long upstreamEnterpriseId;

    private String upstreamEnterpriseName;
    private String upstreamProvinceCode;
    private String upstreamProvinceName;
    private String upstreamCityCode;
    private String upstreamCityName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long upstreamBatchId;

    private String upstreamBatchNo;

    private String productVariety;

    /** 1 养殖，2 捕捞 */
    private Integer sourceType;

    /** 1 新建，2 待确认，3 已确认/已发布，4 已下架 */
    private Integer batchStatus;

    /** 本环节交接温度（℃） */
    private BigDecimal handoverTemp;

    /** 本环节冷链是否合格：1 合格，0 不合格 */
    private Integer coldChainOk;

    private LocalDateTime publishTime;

    private LocalDateTime offShelfTime;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
