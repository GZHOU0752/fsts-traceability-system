package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前企业注册信息（接口 4.5 / 8.1，二者结构一致）。
 *
 * <p>非本企业类型的资质字段返回 null；不含 password、deleted 等敏感字段。
 */
@Data
@Builder
public class EnterpriseInfoVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long enterpriseId;

    private String enterpriseCode;

    private String enterpriseName;

    private Integer enterpriseType;

    private String enterpriseTypeName;

    private String loginName;

    private String creditCode;

    private String legalPerson;

    private String contactPerson;

    private String contactPhone;

    private String provinceCode;

    private String provinceName;

    private String cityCode;

    private String cityName;

    private String address;

    // ---- 按企业类型选择性返回 ----
    private String fisheryLicenseNo;
    private String aquacultureLicenseNo;
    private String fryLicenseNo;
    private String foodProductionLicenseNo;
    private String exportFilingNo;
    private String foodBusinessLicenseNo;
    private String roadTransportLicenseNo;
    private BigDecimal coldStorageCapacity;
    private String transportToolInfo;
    private String displayEquipmentInfo;

    private LocalDateTime registerTime;

    private LocalDateTime lastLoginTime;

    /** 前端可编辑字段，本系统固定为 ["password"] */
    private List<String> editableFields;
}
