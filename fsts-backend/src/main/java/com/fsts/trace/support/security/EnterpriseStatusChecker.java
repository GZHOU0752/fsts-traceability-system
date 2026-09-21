package com.fsts.trace.support.security;

import com.fsts.trace.common.Constants;
import com.fsts.trace.entity.NodeEnterprise;
import com.fsts.trace.mapper.NodeEnterpriseMapper;
import com.fsts.trace.support.cache.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 企业状态校验（带缓存）：解决"管理员停用企业后，旧令牌仍可访问"的窗口问题。
 *
 * <p>权衡说明：若每个请求都查库，会给数据库带来不必要的常量压力；
 * 若完全不校验，令牌在有效期内始终可用。这里取中间方案——
 * 结果缓存 60 秒，把"停用生效延迟"从 2 小时压缩到 1 分钟，
 * 而数据库压力仅为"每企业每分钟 1 次"。
 */
@Slf4j
@Component
public class EnterpriseStatusChecker {

    private static final String NAMESPACE = "enterprise:status";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final NodeEnterpriseMapper enterpriseMapper;
    private final CacheService cacheService;

    public EnterpriseStatusChecker(NodeEnterpriseMapper enterpriseMapper, CacheService cacheService) {
        this.enterpriseMapper = enterpriseMapper;
        this.cacheService = cacheService;
    }

    /**
     * 判断企业当前是否可访问。
     */
    public boolean isActive(Long enterpriseId) {
        if (enterpriseId == null) {
            return false;
        }
        String key = String.valueOf(enterpriseId);
        Boolean cached = cacheService.get(NAMESPACE, key, Boolean.class);
        if (cached != null) {
            return cached;
        }
        NodeEnterprise enterprise = enterpriseMapper.selectById(enterpriseId);
        boolean active = enterprise != null
                && enterprise.getStatus() != null
                && enterprise.getStatus() == Constants.STATUS_ENABLED;
        cacheService.put(NAMESPACE, key, active, TTL);
        return active;
    }

    /**
     * 管理员停用企业后立即让其状态缓存失效，避免 60 秒延迟。
     */
    public void evict(Long enterpriseId) {
        if (enterpriseId != null) {
            cacheService.evict(NAMESPACE, String.valueOf(enterpriseId));
        }
    }
}
