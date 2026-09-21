package com.fsts.trace.dto.stat;

import lombok.Data;

/**
 * 企业类型分布的数据库行（对应接口 7.5）。
 */
@Data
public class TypeStatRow {

    private Integer enterpriseType;
    private String enterpriseTypeName;
    private Long cnt;
}
