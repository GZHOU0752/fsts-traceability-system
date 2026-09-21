package com.fsts.trace.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 溯源链环节温度明细（{@code temperatures} 元素）。
 */
@Data
@AllArgsConstructor
public class TemperatureItemVO {

    private String name;

    private BigDecimal value;

    /** 判定阈值（℃） */
    private BigDecimal threshold;

    private Boolean qualified;
}
