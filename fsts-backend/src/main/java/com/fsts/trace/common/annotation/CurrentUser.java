package com.fsts.trace.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入当前登录主体（LoginUser）到 Controller 方法参数。
 *
 * <p>用法：public Result&lt;?&gt; info(@CurrentUser LoginUser user)。
 * 相比在方法体里到处写 UserContext.require()，显式声明依赖更清晰、也更易测试。
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
