package com.fsts.trace.common;

/**
 * 当前登录用户上下文（ThreadLocal）。
 *
 * <p>由 {@code AuthInterceptor} 写入、{@code afterCompletion} 清理，
 * 配合线程池使用时必须保证清理，否则会造成租户数据串号（严重安全问题）。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 获取当前登录主体，未登录直接抛 401。
     */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    /**
     * 获取当前登录企业主体，非企业身份抛 403。
     */
    public static LoginUser requireEnterprise() {
        LoginUser user = require();
        if (!Constants.USER_TYPE_ENTERPRISE.equals(user.getUserType()) || user.getEnterpriseId() == null) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "请使用节点企业账号访问");
        }
        return user;
    }

    /**
     * 获取当前登录企业 ID（数据隔离核心）。
     */
    public static Long currentEnterpriseId() {
        return requireEnterprise().getEnterpriseId();
    }

    /**
     * 获取当前登录企业类型。
     */
    public static Integer currentEnterpriseType() {
        return requireEnterprise().getEnterpriseType();
    }
}
