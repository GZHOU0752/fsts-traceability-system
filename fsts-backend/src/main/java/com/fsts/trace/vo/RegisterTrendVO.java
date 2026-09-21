package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 十二个月注册趋势（接口 7.2）。
 *
 * <p>months 与 counts 等长、下标一一对应，无数据的月份补 0。
 */
@Data
@Builder
public class RegisterTrendVO {

    private List<String> months;

    private List<Long> counts;

    private Long total;
}
