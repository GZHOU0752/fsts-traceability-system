package com.fsts.trace.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 确认进场请求体（接口 11.3），处理说明可选。
 */
@Data
public class HandleRemarkRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Size(max = 200, message = "处理说明长度不能超过 200 个字符")
    private String handleRemark;
}
