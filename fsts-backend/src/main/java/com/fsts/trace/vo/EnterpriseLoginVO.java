package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

/**
 * 节点企业登录响应（接口 4.4）。
 */
@Data
@Builder
public class EnterpriseLoginVO {

    private String token;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long enterpriseId;

    private String enterpriseCode;

    private String enterpriseName;

    private Integer enterpriseType;

    private String enterpriseTypeName;

    private String provinceName;

    private String cityName;

    private Long expiresIn;
}
