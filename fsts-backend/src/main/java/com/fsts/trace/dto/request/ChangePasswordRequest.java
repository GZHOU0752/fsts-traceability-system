package com.fsts.trace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改本企业密码请求体（接口 4.6）。
 */
@Data
public class ChangePasswordRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度须为 6~20 位")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    @Override
    public String toString() {
        return "ChangePasswordRequest{password='******'}";
    }
}
