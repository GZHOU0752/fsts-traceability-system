package com.fsts.trace.common;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录主体：由 JWT 解析得到，贯穿 Controller -> Service。
 *
 * <p>数据隔离的根：企业端所有查询条件中的 enterpriseId 一律取自本对象，绝不接受前端传入。
 */
@Data
@Builder
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 管理员 ID 或企业 ID */
    private Long userId;

    /** ADMIN / ENTERPRISE */
    private String userType;

    /** 节点企业 ID（管理员为 null） */
    private Long enterpriseId;

    /** 节点企业类型 1/2/3/4（管理员为 null） */
    private Integer enterpriseType;

    /** 节点企业名称（管理员为 null） */
    private String enterpriseName;

    /** 登录账号 */
    private String loginName;

    /**
     * 令牌唯一标识（JWT 的 jti 声明）。
     *
     * <p>退出登录 / 改密码时用它做黑名单键。必须是随机值：
     * 若用签名摘要之类"由载荷决定"的值，同一秒内为同一用户签发的两个令牌会完全相同，
     * 导致新令牌一签发就被旧的黑名单命中（表现为"刚登录就提示登录已失效"）。
     */
    private String tokenId;

    /** Token 签发时间（秒） */
    private Long issuedAt;

    /** Token 过期时间（秒），用于退出登录时计算黑名单有效期 */
    private Long expiresAt;

    public boolean isAdmin() {
        return Constants.USER_TYPE_ADMIN.equals(userType);
    }
}
