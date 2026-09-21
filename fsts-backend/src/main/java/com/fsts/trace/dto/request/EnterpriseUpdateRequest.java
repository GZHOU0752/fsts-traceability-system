package com.fsts.trace.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新节点企业请求体（接口 6.4）。
 *
 * <p>与新建的差异：本接口不接收 password；{@code enterpriseType} 不允许修改，
 * 一旦传入由服务层返回 {@code code = 400}（已注册企业变更类型会破坏溯源链）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EnterpriseUpdateRequest extends EnterpriseSaveRequest {

    private Integer enterpriseType;
}
