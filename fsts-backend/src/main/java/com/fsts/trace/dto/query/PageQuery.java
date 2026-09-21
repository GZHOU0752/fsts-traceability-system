package com.fsts.trace.dto.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页入参基类。
 *
 * <p>上限统一收敛在 MyBatis-Plus 分页插件（单页最多 500 条），
 * 此处仅做默认值与下界保护，防止 size=0 或负数造成异常。
 */
@Data
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long current = 1L;

    private Long size = 10L;

    public long safeCurrent() {
        return current == null || current < 1 ? 1L : current;
    }

    public long safeSize() {
        if (size == null || size < 1) {
            return 10L;
        }
        return size;
    }
}
