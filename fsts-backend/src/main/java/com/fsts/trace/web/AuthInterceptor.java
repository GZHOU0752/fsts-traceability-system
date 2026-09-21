package com.fsts.trace.web;

import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.Constants;
import com.fsts.trace.common.ErrorCode;
import com.fsts.trace.common.LoginUser;
import com.fsts.trace.common.UserContext;
import com.fsts.trace.support.security.EnterpriseStatusChecker;
import com.fsts.trace.support.security.JwtUtil;
import com.fsts.trace.support.security.TokenBlacklist;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：解析 JWT、校验角色、写入用户上下文。
 *
 * <p>三端隔离落地方式：按路径前缀判定访问主体，
 * {@code /api/admin/**} 只认管理员令牌，{@code /api/enterprise/**} 只认企业令牌，
 * 越权访问直接 403。
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_TOKEN = "fsts.token";

    private static final String ADMIN_PREFIX = "/api/admin/";
    private static final String ENTERPRISE_PREFIX = "/api/enterprise/";

    private final JwtUtil jwtUtil;
    private final TokenBlacklist tokenBlacklist;
    private final EnterpriseStatusChecker enterpriseStatusChecker;

    public AuthInterceptor(JwtUtil jwtUtil, TokenBlacklist tokenBlacklist,
                           EnterpriseStatusChecker enterpriseStatusChecker) {
        this.jwtUtil = jwtUtil;
        this.tokenBlacklist = tokenBlacklist;
        this.enterpriseStatusChecker = enterpriseStatusChecker;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = JwtUtil.resolveBearer(request.getHeader("Authorization"));
        LoginUser user = jwtUtil.parseToken(token);

        if (tokenBlacklist.contains(user.getTokenId())) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        String uri = request.getRequestURI();
        if (uri.startsWith(ADMIN_PREFIX)) {
            if (!Constants.USER_TYPE_ADMIN.equals(user.getUserType())) {
                throw BusinessException.of(ErrorCode.FORBIDDEN, "无操作权限");
            }
        } else if (uri.startsWith(ENTERPRISE_PREFIX)) {
            if (!Constants.USER_TYPE_ENTERPRISE.equals(user.getUserType()) || user.getEnterpriseId() == null) {
                throw BusinessException.of(ErrorCode.FORBIDDEN, "无操作权限");
            }
            // 企业被停用后，旧令牌最多再存活 60 秒即被拒绝
            if (!enterpriseStatusChecker.isActive(user.getEnterpriseId())) {
                throw BusinessException.of(ErrorCode.ACCOUNT_DISABLED);
            }
        }

        UserContext.set(user);
        request.setAttribute(ATTR_TOKEN, token);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 线程复用必须清理，否则会造成跨用户的数据串号
        UserContext.clear();
    }
}
