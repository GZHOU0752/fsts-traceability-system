package com.fsts.trace.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Objects;

/**
 * 全局异常处理：把各类异常收敛成统一响应结构，避免异常堆栈泄漏给前端。
 *
 * <p>HTTP 状态码策略（与接口文档 2.4 一致）：
 * <ul>
 *   <li>401 / 403：真实返回对应 HTTP 状态码，便于前端拦截器统一跳登录页；</li>
 *   <li>其余业务错误：HTTP 一律 200，由 {@code code} 表达业务结果。</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e, HttpServletRequest request) {
        int code = e.getCode();
        if (code == ErrorCode.UNAUTHORIZED.getCode()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.fail(e.getCode(), e.getMessage()));
        }
        if (code == ErrorCode.FORBIDDEN.getCode()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.fail(e.getCode(), e.getMessage()));
        }
        // 业务异常属于预期内分支，用 warn 记录即可，避免污染错误日志
        log.warn("业务异常 uri={} code={} msg={}", request.getRequestURI(), code, e.getMessage());
        return ResponseEntity.ok(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(ErrorCode.PARAM_INVALID.getMessage());
        return ResponseEntity.ok(Result.fail(ErrorCode.PARAM_INVALID, message));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(ErrorCode.PARAM_INVALID.getMessage());
        return ResponseEntity.ok(Result.fail(ErrorCode.PARAM_INVALID, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse(ErrorCode.PARAM_INVALID.getMessage());
        return ResponseEntity.ok(Result.fail(ErrorCode.PARAM_INVALID, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResponseEntity.ok(Result.fail(ErrorCode.PARAM_INVALID, "请求体格式不正确"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.ok(Result.fail(ErrorCode.PARAM_INVALID, "缺少必填参数：" + e.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.ok(Result.fail(ErrorCode.PARAM_INVALID, "参数类型不正确：" + e.getName()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNoHandler(NoHandlerFoundException e) {
        return ResponseEntity.ok(Result.fail(ErrorCode.NOT_FOUND, "接口不存在：" + e.getRequestURL()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.ok(Result.fail(ErrorCode.PARAM_INVALID, "请求方法不支持：" + e.getMethod()));
    }

    /**
     * 唯一索引冲突：高并发下"先查后插"存在竞态，必须由数据库唯一索引兜底。
     * 这里把索引名翻译成文档定义的业务错误码，保证前端提示与契约一致。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException e) {
        String raw = Objects.toString(e.getMostSpecificCause().getMessage(), "");
        log.warn("唯一索引冲突: {}", raw);
        if (raw.contains("uk_batch_enterprise_no")) {
            return ResponseEntity.ok(Result.fail(ErrorCode.BATCH_NO_EXISTS));
        }
        if (raw.contains("uk_enterprise_login_name")
                || raw.contains("uk_enterprise_credit_code")
                || raw.contains("uk_enterprise_code")) {
            return ResponseEntity.ok(Result.fail(ErrorCode.ENTERPRISE_EXISTS));
        }
        if (raw.contains("uk_request_batch")) {
            return ResponseEntity.ok(Result.fail(ErrorCode.CONFLICT, "该批号已存在待确认的进场确认请求"));
        }
        if (raw.contains("uk_trace_code") || raw.contains("uk_trace_batch")) {
            return ResponseEntity.ok(Result.fail(ErrorCode.CONFLICT, "溯源标识码生成冲突，请重试"));
        }
        return ResponseEntity.ok(Result.fail(ErrorCode.CONFLICT, "数据已存在，请勿重复提交"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDataAccess(DataAccessException e) {
        log.error("数据库访问异常", e);
        return ResponseEntity.ok(Result.fail(ErrorCode.SYSTEM_ERROR));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 uri={}", request.getRequestURI(), e);
        return ResponseEntity.ok(Result.fail(ErrorCode.SYSTEM_ERROR));
    }
}
