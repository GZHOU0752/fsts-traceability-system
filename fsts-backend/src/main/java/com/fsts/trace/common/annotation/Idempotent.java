package com.fsts.trace.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等保护：拦截同一主体短时间内对同一接口的完全相同请求体。
 *
 * <p>适用场景：双击提交、网络重试导致的重复写操作
 * （新建企业、新建批号、发送确认请求、确认/拒绝进场等）。
 *
 * <p>注意：这是"性能优化 + 体验优化"，正确性仍由数据库唯一索引与 CAS 更新保证。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 生效窗口（秒），窗口内相同请求视为重复 */
    int ttlSeconds() default 3;

    /** 命中重复时的提示信息 */
    String message() default "操作正在处理中，请勿重复提交";
}
