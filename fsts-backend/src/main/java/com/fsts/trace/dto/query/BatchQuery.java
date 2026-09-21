package com.fsts.trace.dto.query;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 本企业产品批号分页查询入参（接口 10.1）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BatchQuery extends PageQuery {

    /** 状态分组：1 新建、2 待确认、3 已确认/已发布（必填，已下架不返回） */
    @NotNull(message = "批号状态不能为空")
    private Integer batchStatus;

    private String batchNo;

    private String productVariety;

    /** 上游（进场）产品批号，模糊匹配 */
    private String upstreamBatchNo;
}
