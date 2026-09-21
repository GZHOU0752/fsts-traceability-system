package com.fsts.trace.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 下架产品批号请求体（接口 10.7），{@code remark} 为下架原因。
 */
@Data
public class OffShelfRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Size(max = 255, message = "下架原因长度不能超过 255 个字符")
    private String remark;
}
