package com.fsts.trace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录请求体（接口 4.1 / 4.4 共用）。
 *
 * <p>安全约定：password 字段禁止写入日志（日志中通过掩码处理）。
 */
@Data
public class LoginRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "登录账号不能为空")
    @Size(max = 50, message = "登录账号长度不能超过 50 个字符")
    private String loginName;

    @NotBlank(message = "登录密码不能为空")
    @Size(max = 64, message = "登录密码长度不合法")
    private String password;

    @Override
    public String toString() {
        return "LoginRequest{loginName='" + loginName + "', password='******'}";
    }
}
