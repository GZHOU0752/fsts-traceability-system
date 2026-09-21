package com.fsts.trace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 节点企业注册信息（node_enterprise）。
 *
 * <p>四类企业共用一张表，资质字段按类型差异化填写，非本类型字段保持 null。
 */
@Data
@TableName("node_enterprise")
public class NodeEnterprise implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 企业编码，后端生成：FSTS-E-{年份}{4 位流水} */
    private String enterpriseCode;

    private String enterpriseName;

    /** 1 捕捞与养殖，2 冷冻加工，3 批发商，4 零售商 */
    private Integer enterpriseType;

    private String loginName;

    /** BCrypt 密文，禁止序列化 */
    @JsonIgnore
    private String password;

    private String creditCode;

    private String legalPerson;

    private String contactPerson;

    private String contactPhone;

    private String provinceCode;

    private String provinceName;

    private String cityCode;

    private String cityName;

    private String address;

    private LocalDateTime registerTime;

    // ---- 类型 1：捕捞与养殖企业 ----
    private String fisheryLicenseNo;
    private String aquacultureLicenseNo;
    private String fryLicenseNo;

    // ---- 类型 2：冷冻加工企业 ----
    private String foodProductionLicenseNo;
    private String exportFilingNo;

    // ---- 类型 3 / 4：批发商、零售商 ----
    private String foodBusinessLicenseNo;
    private String roadTransportLicenseNo;

    // ---- 类型 3：批发商经营能力 ----
    private BigDecimal coldStorageCapacity;
    private String transportToolInfo;

    // ---- 类型 4：零售商设备 ----
    private String displayEquipmentInfo;

    /** 1 正常，0 停用 */
    private Integer status;

    private LocalDateTime lastLoginTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：1 已删除（历史溯源链数据需保留，故不物理删除） */
    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
