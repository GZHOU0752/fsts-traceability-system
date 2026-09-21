package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消费者端溯源信息（接口 12.1）。
 */
@Data
@Builder
public class PublicTraceVO {

    private String traceCode;

    private String batchNo;

    private String productVariety;

    private String retailerName;

    private String saleStore;

    private LocalDateTime generateTime;

    private Integer queryCount;

    /** 全链条温度是否全部合格 */
    private Boolean coldChainQualified;

    private String coldChainConclusion;

    private List<TemperaturePointVO> temperatureCurve;

    private List<TraceLinkVO> links;
}
