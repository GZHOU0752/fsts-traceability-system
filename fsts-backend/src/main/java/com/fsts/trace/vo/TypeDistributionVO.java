package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 企业类型分布（接口 7.5，饼图）。
 */
@Data
@Builder
public class TypeDistributionVO {

    private Integer enterpriseType;

    private String enterpriseTypeName;

    private Long count;

    private BigDecimal percent;
}
