package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 溯源链环节（接口 12.1 {@code links} 元素），按环节顺序 1->2->3->4 排列。
 *
 * <p>仅包含公开信息（企业名称、省市），不含联系人、联系电话、账号等敏感字段。
 */
@Data
@Builder
public class TraceLinkVO {

    private Integer stageCode;

    private String stageName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long enterpriseId;

    private String enterpriseName;

    private String enterpriseTypeName;

    private String provinceName;

    private String cityName;

    private String batchNo;

    private String upstreamBatchNo;

    private String productVariety;

    private String sourceTypeName;

    private BigDecimal handoverTemp;

    private Boolean coldChainOk;

    private LocalDateTime handoverTime;

    private List<CertificateVO> certificates;

    private List<TemperatureItemVO> temperatures;
}
