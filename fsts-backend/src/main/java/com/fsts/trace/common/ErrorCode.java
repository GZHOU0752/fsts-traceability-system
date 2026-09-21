package com.fsts.trace.common;

import lombok.Getter;

/**
 * 统一业务状态码（与《前后端接口文档》3.6 错误码一一对应）。
 *
 * <p>约定：除 401 / 403 外，HTTP 状态码一律为 200，业务结果由 {@code code} 表达。
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功"),

    PARAM_INVALID(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已失效"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "状态冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试"),

    LOGIN_FAILED(1001, "账号或密码错误"),
    ACCOUNT_DISABLED(1002, "账号已停用"),

    BATCH_NO_EXISTS(2001, "产品批号已存在"),
    ENTERPRISE_EXISTS(2002, "企业名称、登录账号或营业执照编号已存在"),
    UPSTREAM_BATCH_INVALID(2003, "上游产品批号不存在或未发布"),
    COLD_CHAIN_ABNORMAL(2004, "温度不符合冷链要求"),
    BATCH_STATUS_NOT_ALLOWED(2005, "批号状态不允许该操作"),

    TRACE_CODE_NOT_FOUND(3001, "未查询到该溯源标识码"),
    TRACE_CODE_INVALID(3002, "该批次商品已停止追溯服务");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
