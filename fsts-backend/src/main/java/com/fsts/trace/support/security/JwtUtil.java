package com.fsts.trace.support.security;

import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.ErrorCode;
import com.fsts.trace.common.LoginUser;
import com.fsts.trace.config.props.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 工具：签发与解析令牌。
 *
 * <p>无状态鉴权是"水平扩容"的前提——服务端不保存会话，
 * 任意实例都能独立校验令牌，Nginx 无需会话保持（ip_hash / sticky）。
 */
@Slf4j
@Component
public class JwtUtil {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USER_TYPE = "userType";
    private static final String CLAIM_ENTERPRISE_ID = "enterpriseId";
    private static final String CLAIM_ENTERPRISE_TYPE = "enterpriseType";
    private static final String CLAIM_ENTERPRISE_NAME = "enterpriseName";
    private static final String CLAIM_LOGIN_NAME = "loginName";

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("fsts.jwt.secret 长度必须不少于 32 字节，当前 " + keyBytes.length);
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 签发令牌。
     */
    public String createToken(LoginUser user) {
        long now = System.currentTimeMillis();
        long expireMillis = now + properties.getExpireSeconds() * 1000L;

        return Jwts.builder()
                // jti：令牌唯一标识，保证同一秒内为同一用户签发的令牌也互不相同
                .id(UUID.randomUUID().toString())
                .issuer(properties.getIssuer())
                .subject(String.valueOf(user.getUserId()))
                .claims(Map.of(
                        CLAIM_USER_ID, String.valueOf(user.getUserId()),
                        CLAIM_USER_TYPE, user.getUserType(),
                        CLAIM_ENTERPRISE_ID, user.getEnterpriseId() == null ? "" : String.valueOf(user.getEnterpriseId()),
                        CLAIM_ENTERPRISE_TYPE, user.getEnterpriseType() == null ? "" : String.valueOf(user.getEnterpriseType()),
                        CLAIM_ENTERPRISE_NAME, user.getEnterpriseName() == null ? "" : user.getEnterpriseName(),
                        CLAIM_LOGIN_NAME, user.getLoginName() == null ? "" : user.getLoginName()))
                .issuedAt(new Date(now))
                .expiration(new Date(expireMillis))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析令牌并还原为登录主体。
     *
     * @throws BusinessException 令牌缺失 / 过期 / 非法时抛出 401
     */
    public LoginUser parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED);
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return LoginUser.builder()
                    .userId(toLong(claims.get(CLAIM_USER_ID, String.class)))
                    .userType(claims.get(CLAIM_USER_TYPE, String.class))
                    .enterpriseId(toLong(claims.get(CLAIM_ENTERPRISE_ID, String.class)))
                    .enterpriseType(toInteger(claims.get(CLAIM_ENTERPRISE_TYPE, String.class)))
                    .enterpriseName(claims.get(CLAIM_ENTERPRISE_NAME, String.class))
                    .loginName(claims.get(CLAIM_LOGIN_NAME, String.class))
                    .tokenId(claims.getId())
                    .issuedAt(claims.getIssuedAt() == null ? null : claims.getIssuedAt().getTime() / 1000)
                    .expiresAt(claims.getExpiration() == null ? null : claims.getExpiration().getTime() / 1000)
                    .build();
        } catch (ExpiredJwtException e) {
            log.debug("令牌已过期: {}", e.getMessage());
            throw BusinessException.of(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("令牌解析失败: {}", e.getMessage());
            throw BusinessException.of(ErrorCode.UNAUTHORIZED);
        }
    }

    /**
     * Token 剩余有效期（秒），用于计算黑名单缓存时长。
     */
    public long remainSeconds(LoginUser user) {
        if (user.getExpiresAt() == null) {
            return properties.getExpireSeconds();
        }
        long remain = user.getExpiresAt() - System.currentTimeMillis() / 1000;
        return Math.max(remain, 0L);
    }

    public Long expireSeconds() {
        return properties.getExpireSeconds();
    }

    public static String resolveBearer(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        String prefix = "Bearer ";
        if (authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return authorizationHeader.substring(prefix.length()).trim();
        }
        return authorizationHeader.trim();
    }

    private Long toLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
