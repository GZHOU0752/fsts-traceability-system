package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消费者端产品搜索结果项。
 */
@Data
@Builder
public class PublicProductVO {

    private String traceCode;

    private String batchNo;

    private String productVariety;

    private String retailerName;

    private String saleStore;

    private LocalDateTime generateTime;
}
