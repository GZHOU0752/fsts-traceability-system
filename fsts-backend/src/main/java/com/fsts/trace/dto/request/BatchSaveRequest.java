package com.fsts.trace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新建产品批号请求体（接口 10.3）。
 *
 * <p>设计说明：接口路径统一，字段随登录企业类型变化。这里用"扁平全类型汇总"承载，
 * 由服务层的类型校验器（{@code BatchValidator} 策略）按企业类型做差异化必填校验。
 *
 * <p>此处只对"全类型通用"的 batchNo 加必填注解，其余字段的必填规则交给校验器，
 * 因为对类型 2/3/4 而言 productVariety、sourceType 由上游带出，前端不传是合法的。
 */
@Data
public class BatchSaveRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "产品批号不能为空")
    @Size(max = 60, message = "产品批号长度不能超过 60 个字符")
    private String batchNo;

    /** 产品品种（类型 1 必填；类型 2/3/4 由上游带出） */
    private String productVariety;

    /** 来源类型：1 养殖，2 捕捞（类型 1 必填；其余继承上游） */
    private Integer sourceType;

    /** 类型 1：是否同时发布 */
    private Boolean publish;

    /** 类型 2/3/4：是否同时向上游发送确认请求 */
    private Boolean sendConfirmRequest;

    /** 温度超限时是否强制提交（前端二次确认后置 true） */
    private Boolean forceSubmit;

    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    private String remark;

    // ---------------- 类型 1：捕捞与养殖企业 ----------------
    private LocalDate catchBreedDate;
    private Integer certificateType;
    private String certificateNo;
    private BigDecimal departureTemp;
    private String drugReportNo;
    private String fishingLogNo;

    // ---------------- 类型 2 / 3 / 4：上游来源 ----------------
    private Long upstreamEnterpriseId;
    private Long upstreamBatchId;

    // ---------------- 类型 2：冷冻加工企业 ----------------
    private String processForm;
    private String inspectionNo;
    private BigDecimal quickFreezeTemp;
    private BigDecimal factoryTemp;
    private String productionBatchNo;

    // ---------------- 类型 3：批发商 ----------------
    private LocalDate wholesaleDate;
    private BigDecimal inboundTemp;
    private BigDecimal coldStorageTemp;
    private BigDecimal outboundTemp;
    private String transportToolNo;
    private String coldStorageNo;

    // ---------------- 类型 4：零售商 ----------------
    private LocalDate shelfDate;
    private BigDecimal displayTemp;
    private String saleStore;
    private String displayEquipNo;
}
