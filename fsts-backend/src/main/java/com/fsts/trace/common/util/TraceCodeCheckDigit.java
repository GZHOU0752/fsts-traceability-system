package com.fsts.trace.common.util;

import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.ErrorCode;
import org.springframework.util.StringUtils;

/**
 * 溯源码校验位工具。
 *
 * <p><b>为什么需要：</b>溯源码主要通过扫码进入系统，但包装磨损、人工录入时仍会出现"只差一位"
 * 的情况。没有校验位时，错一位只能得到"未查询到该溯源标识码"，用户无法判断是自己输错了
 * 还是这批货真的没有记录；加上一位校验位就能明确区分这两种情况。
 *
 * <p><b>编码规则（向后兼容）：</b>
 * <ul>
 *   <li>主干码形如 {@code FSTS-20260915-SH-0001}（历史数据与演示数据都是这种形态）；</li>
 *   <li>开启校验位后，新生成的码为 {@code FSTS-20260915-SH-0001-7}，最后一段即校验位；</li>
 *   <li>查询时：带校验位的码必须校验通过（否则返回 3001 并提示"校验位不正确"），
 *       不带校验位的历史码按原样查询，因此不需要重建任何演示数据。</li>
 * </ul>
 *
 * <p>算法：对主体（去掉 FSTS 前缀、去掉分隔符、去掉校验位后的大写字符串）逐位加权求和，
 * 数字按本身取值、字母按 A=10 取值，权重按位置在 7 / 3 之间交替，最后对 10 取模。
 * 交替权重可以让"相邻两位写反"这种最常见的手误也能被检出。
 */
public final class TraceCodeCheckDigit {

    /** 正规形态的段数：FSTS / 日期 / 省简称 / 流水 */
    private static final int BASE_SEGMENTS = 4;

    private TraceCodeCheckDigit() {
    }

    /** 归一化：去空格、转大写（用户输入常带空格且大小写混写） */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase().replace(" ", "");
    }

    /** 是否已经带校验位（段数 = 5） */
    public static boolean hasCheckDigit(String code) {
        String value = normalize(code);
        return value.split("-").length == BASE_SEGMENTS + 1;
    }

    /** 取主干码（去掉校验位那一段） */
    public static String stripCheckDigit(String code) {
        String value = normalize(code);
        if (!hasCheckDigit(value)) {
            return value;
        }
        return value.substring(0, value.lastIndexOf('-'));
    }

    /** 计算校验位（0~9） */
    public static int compute(String code) {
        String body = body(stripCheckDigit(code));
        int sum = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            int value = Character.isDigit(c) ? c - '0' : (c - 'A' + 10);
            sum += value * ((i % 2 == 0) ? 7 : 3);
        }
        return Math.abs(sum) % 10;
    }

    /** 给主干码追加校验位；已带校验位时返回原值 */
    public static String append(String code) {
        String value = normalize(code);
        if (!StringUtils.hasText(value) || hasCheckDigit(value)) {
            return value;
        }
        return value + "-" + compute(value);
    }

    /**
     * 校验并返回可用于数据库查询的码。
     *
     * @param raw     用户输入 / 扫码得到的原始内容
     * @param enabled 是否启用校验（{@code fsts.trace.check-digit-enabled}）
     * @return 归一化后的码（带校验位则保留，历史码原样返回）
     * @throws BusinessException 校验位与主体不匹配时抛出（沿用 3001，文案说明是校验位问题）
     */
    public static String assertValid(String raw, boolean enabled) {
        String value = normalize(raw);
        if (value.isEmpty()) {
            throw BusinessException.of(ErrorCode.TRACE_CODE_NOT_FOUND);
        }
        if (!enabled || !hasCheckDigit(value)) {
            return value;
        }
        int expected = compute(value);
        int actual = value.charAt(value.length() - 1) - '0';
        if (actual != expected) {
            throw BusinessException.of(ErrorCode.TRACE_CODE_NOT_FOUND,
                    "溯源码校验位不正确，请核对是否输入有误（正确校验位应为 " + expected + "）");
        }
        return value;
    }

    /** 主体：去掉前缀 FSTS、去掉分隔符、去掉校验位 */
    private static String body(String baseCode) {
        String value = baseCode.toUpperCase();
        if (value.startsWith("FSTS-")) {
            value = value.substring("FSTS-".length());
        }
        return value.replace("-", "");
    }
}
