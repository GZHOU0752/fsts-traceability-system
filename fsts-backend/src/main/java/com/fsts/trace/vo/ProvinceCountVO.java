package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 省分组注册数量（接口 7.4，柱状图），直接给出 ECharts 的 xAxis / series 数据。
 */
@Data
@Builder
public class ProvinceCountVO {

    private List<String> provinces;

    private List<Long> counts;
}
