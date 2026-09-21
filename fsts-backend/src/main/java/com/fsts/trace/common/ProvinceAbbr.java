package com.fsts.trace.common;

import java.util.Map;

/**
 * 省级行政区划简称拼音首字母表，用于生成溯源标识码 FSTS-yyyyMMdd-{省简称}-{流水号}。
 *
 * <p>为什么不实时算拼音：溯源码生成处于高并发写路径，引入拼音库既增加依赖也增加 CPU 开销；
 * 省级区划固定为 34 个，用常量表查表是 O(1) 且结果稳定可预期。
 * 若后续扩展为"市级编码"，在此表基础上补充即可。
 */
public final class ProvinceAbbr {

    private static final Map<String, String> ABBR = Map.ofEntries(
            Map.entry("110000", "BJ"), Map.entry("120000", "TJ"), Map.entry("130000", "HE"),
            Map.entry("140000", "SX"), Map.entry("150000", "NM"), Map.entry("210000", "LN"),
            Map.entry("220000", "JL"), Map.entry("230000", "HL"), Map.entry("310000", "SH"),
            Map.entry("320000", "JS"), Map.entry("330000", "ZJ"), Map.entry("340000", "AH"),
            Map.entry("350000", "FJ"), Map.entry("360000", "JX"), Map.entry("370000", "SD"),
            Map.entry("410000", "HA"), Map.entry("420000", "HB"), Map.entry("430000", "HN"),
            Map.entry("440000", "GD"), Map.entry("450000", "GX"), Map.entry("460000", "HI"),
            Map.entry("500000", "CQ"), Map.entry("510000", "SC"), Map.entry("520000", "GZ"),
            Map.entry("530000", "YN"), Map.entry("540000", "XZ"), Map.entry("610000", "SN"),
            Map.entry("620000", "GS"), Map.entry("630000", "QH"), Map.entry("640000", "NX"),
            Map.entry("650000", "XJ"), Map.entry("710000", "TW"), Map.entry("810000", "HK"),
            Map.entry("820000", "MO"));

    /** 未知省份的兜底编码，保证溯源码始终能生成（不因字典缺失而失败） */
    private static final String FALLBACK = "XX";

    private ProvinceAbbr() {
    }

    public static String of(String provinceCode) {
        if (provinceCode == null) {
            return FALLBACK;
        }
        return ABBR.getOrDefault(provinceCode, FALLBACK);
    }
}
