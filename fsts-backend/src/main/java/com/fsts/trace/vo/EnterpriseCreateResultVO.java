package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

/**
 * 新建节点企业响应（接口 6.3）。
 */
@Data
@Builder
public class EnterpriseCreateResultVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String enterpriseCode;

    private String loginName;

    /** 初始密码（仅未指定密码时回显，便于管理员告知企业） */
    private String initialPassword;
}
