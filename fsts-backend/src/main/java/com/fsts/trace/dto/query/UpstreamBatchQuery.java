package com.fsts.trace.dto.query;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 上游企业已发布批号查询入参（接口 9.2）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpstreamBatchQuery extends PageQuery {

    @NotNull(message = "上游企业 ID 不能为空")
    private Long upstreamEnterpriseId;

    private String batchNo;
}
