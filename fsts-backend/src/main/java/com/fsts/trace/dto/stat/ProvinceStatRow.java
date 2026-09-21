package com.fsts.trace.dto.stat;

import lombok.Data;

/**
 * 省分组注册数量的数据库行（对应接口 7.3 / 7.4）。
 */
@Data
public class ProvinceStatRow {

    private String provinceCode;
    private String provinceName;
    private Long cnt;
}
