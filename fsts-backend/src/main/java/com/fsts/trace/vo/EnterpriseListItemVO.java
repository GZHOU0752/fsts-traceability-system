package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点企业列表项（接口 6.1）。
 *
 * <p>按文档业务规则 2，列表不返回资质证件明细，避免响应体过重。
 */
@Data
@Builder
public class EnterpriseListItemVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String enterpriseCode;

    private String enterpriseName;

    private Integer enterpriseType;

    private String enterpriseTypeName;

    private String creditCode;

    private String legalPerson;

    private String contactPerson;

    private String contactPhone;

    private String provinceCode;

    private String provinceName;

    private String cityCode;

    private String cityName;

    private String address;

    private Integer status;

    private String statusName;

    private LocalDateTime registerTime;
}
