package com.fsts.trace.common;

import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常：携带 {@link ErrorCode}，由全局异常处理器转换为统一响应。
 *
 * <p>继承 {@link RuntimeException} 而非受检异常，避免 Service 层方法签名被污染；
 * 同时不填充堆栈（业务异常是预期内的控制流），在高并发下可显著降低开销。
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage(), null, false, false);
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message, null, false, false);
        this.code = errorCode.getCode();
    }

    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    public static BusinessException of(ErrorCode errorCode, String message) {
        return new BusinessException(errorCode, message);
    }
}
