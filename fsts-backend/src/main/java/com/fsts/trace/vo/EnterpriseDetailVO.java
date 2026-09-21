package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 节点企业详情（接口 6.2）。
 */
@Data
@Builder
public class EnterpriseDetailVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

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

    private Integer status;

    private String statusName;

    private LocalDateTime registerTime;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
