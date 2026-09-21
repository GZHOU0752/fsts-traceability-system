package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品批号详情（接口 10.2）。
 *
 * <p>四个明细对象按企业类型只返回其中一个，其余为 null；
 * {@code traceCode} 与 {@code confirmRequest} 仅在存在时返回。
 */
@Data
@Builder
public class BatchDetailVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String batchNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long enterpriseId;

    private String enterpriseName;

    private Integer enterpriseType;

    private String enterpriseTypeName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long upstreamEnterpriseId;

    private String upstreamEnterpriseName;

    private String upstreamProvinceCode;

    private String upstreamProvinceName;

    private String upstreamCityCode;

    private String upstreamCityName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long upstreamBatchId;

    private String upstreamBatchNo;

    private String productVariety;

    private Integer sourceType;

    private String sourceTypeName;

    private Integer batchStatus;

    private String batchStatusName;

    private BigDecimal handoverTemp;

    private Boolean coldChainOk;

    private LocalDateTime publishTime;

    private LocalDateTime offShelfTime;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ---- 按企业类型返回的明细对象 ----
    private BatchFishingInfoVO fishingInfo;

    private BatchProcessingInfoVO processingInfo;

    private BatchWholesaleInfoVO wholesaleInfo;

    private BatchRetailInfoVO retailInfo;

    private TraceCodeInfoVO traceCode;

    private ConfirmRequestBriefVO confirmRequest;
}
