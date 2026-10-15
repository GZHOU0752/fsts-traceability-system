package com.fsts.trace.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 产品批号批量导入请求体（接口 10.12）。
 */
@Data
public class BatchImportRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 单次导入上限：既防止误传超大文件，也保证请求在事务超时内完成 */
    public static final int MAX_ROWS = 500;

    @NotEmpty(message = "导入内容不能为空")
    @Size(max = MAX_ROWS, message = "单次最多导入 " + MAX_ROWS + " 条")
    private List<BatchImportRow> rows;
}
