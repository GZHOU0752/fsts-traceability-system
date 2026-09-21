package com.fsts.trace.dto.stat;

import lombok.Data;

/**
 * 月度注册趋势的数据库行（对应接口 7.2），月份格式 yyyy-MM。
 */
@Data
public class RegisterTrendRow {

    private String month;
    private Long cnt;
}
