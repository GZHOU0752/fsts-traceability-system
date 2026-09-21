package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

/**
 * 系统管理员登录响应（接口 4.1）。
 */
@Data
@Builder
public class AdminLoginVO {

    private String token;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String userType;

    private String loginName;

    private String adminName;

    /** Token 有效期（秒） */
    private Long expiresIn;
}
