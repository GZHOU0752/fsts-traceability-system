package com.fsts.trace.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 唯一性校验结果（接口 6.6 / 10.8）。
 */
@Data
@AllArgsConstructor
public class CheckAvailableVO {

    /** true 可用，false 已存在 */
    private Boolean available;
}
