package com.fsts.trace.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置（fsts.jwt.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fsts.jwt")
public class JwtProperties {

    /** 签名密钥，长度须 >= 32 字节（HS256） */
    private String secret = "fsts-traceability-system-default-secret-key-2026-change-me";

    /** 签发者 */
    private String issuer = "fsts";

    /** 有效期（秒），接口文档约定 7200 */
    private Long expireSeconds = 7200L;
}
