package com.fsts.trace.dto.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 节点企业分页查询入参（接口 6.1）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminEnterpriseQuery extends PageQuery {

    private Long id;

    /** 企业名称，模糊匹配 */
    private String enterpriseName;

    /** 统一社会信用代码 / 营业执照编号，模糊匹配 */
    private String creditCode;

    /** 所在省代码，精确匹配 */
    private String provinceCode;

    /** 所在市代码，精确匹配 */
    private String cityCode;

    /** 企业类型 1/2/3/4 */
    private Integer enterpriseType;

    /** 状态：1 正常，0 停用 */
    private Integer status;
}
