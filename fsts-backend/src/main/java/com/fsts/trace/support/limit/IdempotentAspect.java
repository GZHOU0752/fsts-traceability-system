package com.fsts.trace.support.limit;

import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.ErrorCode;
import com.fsts.trace.common.LoginUser;
import com.fsts.trace.common.UserContext;
import com.fsts.trace.common.annotation.Idempotent;
import com.fsts.trace.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 幂等切面：为标注 {@link Idempotent} 的方法构建请求指纹并占位。
 *
 * <p>指纹 = 用户标识 + 类名.方法名 + 参数摘要。
 * 参数中的 {@code MultipartFile} 等不可序列化对象会被跳过（本系统暂无文件上传，
 * 保留该分支是为了防止后续扩展时踩坑）。
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class IdempotentAspect {

    private final IdempotentGuard guard;

    public IdempotentAspect(IdempotentGuard guard) {
        this.guard = guard;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String key = buildKey(joinPoint);
        Duration ttl = Duration.ofSeconds(Math.max(idempotent.ttlSeconds(), 1));

        if (!guard.tryAcquire(key, ttl)) {
            throw BusinessException.of(ErrorCode.CONFLICT, idempotent.message());
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            // 业务失败立即释放占位，允许用户马上修正后重试
            guard.release(key);
            throw e;
        }
    }

    private String buildKey(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        LoginUser user = UserContext.get();
        String subject = user == null ? "anonymous" : user.getUserType() + ":" + user.getUserId();
        String method = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        return subject + ":" + method + ":" + fingerprint(joinPoint.getArgs());
    }

    private String fingerprint(Object[] args) {
        if (args == null || args.length == 0) {
            return "0";
        }
        List<Object> serializable = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (arg == null || arg instanceof MultipartFile || arg instanceof byte[]) {
                continue;
            }
            serializable.add(arg);
        }
        // 用 JSON 序列化后取摘要，保证同一请求体得到稳定一致的指纹
        return JsonUtils.fingerprint(serializable);
    }
}
