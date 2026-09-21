package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 省分组注册分布（接口 7.3，饼图）。
 */
@Data
@Builder
public class ProvinceDistributionVO {

    private String provinceCode;

    private String provinceName;

    private Long count;

    /** 占比，保留 2 位小数 */
    private BigDecimal percent;
}
