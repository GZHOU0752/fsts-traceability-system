package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 温度曲线数据点（接口 12.1 {@code temperatureCurve}）。
 */
@Data
@Builder
public class TemperaturePointVO {

    private Integer stageCode;

    private String stageName;

    private String enterpriseName;

    private BigDecimal temperature;

    private BigDecimal threshold;

    private Boolean qualified;

    private LocalDateTime recordTime;
}
