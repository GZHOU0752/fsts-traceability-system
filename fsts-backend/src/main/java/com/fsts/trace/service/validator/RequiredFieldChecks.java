package com.fsts.trace.service.validator;

import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.ErrorCode;
import org.springframework.util.StringUtils;

/**
 * 校验小工具：把"必填判断 + 抛出统一错误码"收敛到一处，
 * 避免四个策略实现里到处重复 if 判断与异常构造。
 */
final class RequiredFieldChecks {

    private RequiredFieldChecks() {
    }

    static void notBlank(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, fieldName + "不能为空");
        }
    }

    static void notNull(Object value, String fieldName) {
        if (value == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, fieldName + "不能为空");
        }
    }

    static void inRange(Integer value, int min, int max, String fieldName) {
        if (value == null || value < min || value > max) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, fieldName + "取值必须为 " + min + "~" + max);
        }
    }

    static void notLongerThan(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    fieldName + "长度不能超过 " + maxLength + " 个字符");
        }
    }
}
