package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

/**
 * 上游企业列表项（接口 9.1）。
 *
 * <p>按文档业务规则，不返回上游企业联系人与联系电话。
 */
@Data
@Builder
public class UpstreamEnterpriseVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String enterpriseName;

    private Integer enterpriseType;

    private String enterpriseTypeName;

    private String provinceCode;

    private String provinceName;

    private String cityCode;

    private String cityName;

    private String address;
}
