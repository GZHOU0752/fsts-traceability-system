package com.fsts.trace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 拒绝进场请求体（接口 11.4），拒绝原因必填。
 */
@Data
public class RejectRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "拒绝原因不能为空")
    @Size(max = 200, message = "拒绝原因长度不能超过 200 个字符")
    private String handleRemark;
}
