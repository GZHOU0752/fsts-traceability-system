package com.fsts.trace.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新建节点企业请求体（接口 6.3）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EnterpriseCreateRequest extends EnterpriseSaveRequest {

    @NotNull(message = "企业类型不能为空")
    @Min(value = 1, message = "企业类型取值必须为 1~4")
    @Max(value = 4, message = "企业类型取值必须为 1~4")
    private Integer enterpriseType;

    /** 初始密码，不传则使用默认密码 123456 */
    @Size(min = 6, max = 20, message = "初始密码长度须为 6~20 位")
    private String password;

    @Override
    public String toString() {
        return "EnterpriseCreateRequest{loginName='" + getLoginName() + "', password='******'}";
    }
}
