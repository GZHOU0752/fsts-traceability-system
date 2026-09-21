package com.fsts.trace.dto.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 上游企业查询入参（接口 9.1）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpstreamEnterpriseQuery extends PageQuery {

    private String provinceCode;

    private String cityCode;

    private String enterpriseName;
}
