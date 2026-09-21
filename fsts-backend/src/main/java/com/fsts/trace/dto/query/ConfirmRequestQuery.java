package com.fsts.trace.dto.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待处理进场确认请求查询入参（接口 11.1）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConfirmRequestQuery extends PageQuery {

    /** 请求状态，默认 1（待确认） */
    private Integer requestStatus = 1;

    /** 下游企业名称，模糊查询 */
    private String downstreamEnterpriseName;

    /** 下游产品批号，模糊匹配 */
    private String batchNo;
}
