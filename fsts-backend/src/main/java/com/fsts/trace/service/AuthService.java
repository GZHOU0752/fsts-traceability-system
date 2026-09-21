package com.fsts.trace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.Constants;
import com.fsts.trace.common.ErrorCode;
import com.fsts.trace.common.LoginUser;
import com.fsts.trace.dto.request.ChangePasswordRequest;
import com.fsts.trace.dto.request.LoginRequest;
import com.fsts.trace.entity.NodeEnterprise;
import com.fsts.trace.entity.SysAdmin;
import com.fsts.trace.mapper.NodeEnterpriseMapper;
import com.fsts.trace.mapper.SysAdminMapper;
import com.fsts.trace.service.support.EnterpriseConverter;
import com.fsts.trace.support.security.EnterpriseStatusChecker;
import com.fsts.trace.support.security.JwtUtil;
import com.fsts.trace.support.security.TokenBlacklist;
import com.fsts.trace.vo.AdminInfoVO;
import com.fsts.trace.vo.AdminLoginVO;
import com.fsts.trace.vo.EnterpriseInfoVO;
import com.fsts.trace.vo.EnterpriseLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务：管理员与企业端的登录、登出、当前用户信息、修改密码。
 *
 * <p>安全要点：
 * <ul>
 *   <li>账号不存在与密码错误统一返回 1001，避免账号枚举攻击；</li>
 *   <li>密码明文永不落日志（DTO 的 toString 已做掩码）；</li>
 *   <li>改密码后立即把当前令牌加入黑名单，强制重新登录。</li>
 * </ul>
 */
@Slf4j
@Service
public class AuthService {

    /** 修改密码成功后需要重新登录（接口 4.6 业务规则） */
    private static final List<String> EDITABLE_FIELDS = List.of("password");

    private final SysAdminMapper adminMapper;
    private final NodeEnterpriseMapper enterpriseMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklist tokenBlacklist;
    private final EnterpriseConverter enterpriseConverter;
    private final EnterpriseStatusChecker enterpriseStatusChecker;
    private final DictService dictService;

    public AuthService(SysAdminMapper adminMapper,
                       NodeEnterpriseMapper enterpriseMapper,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       TokenBlacklist tokenBlacklist,
                       EnterpriseConverter enterpriseConverter,
                       EnterpriseStatusChecker enterpriseStatusChecker,
                       DictService dictService) {
        this.adminMapper = adminMapper;
        this.enterpriseMapper = enterpriseMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.tokenBlacklist = tokenBlacklist;
        this.enterpriseConverter = enterpriseConverter;
        this.enterpriseStatusChecker = enterpriseStatusChecker;
        this.dictService = dictService;
    }

    // ------------------------------------------------------------------
    // 系统管理端
    // ------------------------------------------------------------------

    /**
     * 管理员登录（接口 4.1）。
     */
    public AdminLoginVO adminLogin(LoginRequest request) {
        SysAdmin admin = adminMapper.selectOne(Wrappers.<SysAdmin>lambdaQuery()
                .eq(SysAdmin::getLoginName, request.getLoginName())
                .last("LIMIT 1"));

        // 账号不存在与密码错误统一返回 1001，防止账号枚举
        if (admin == null || !passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            log.warn("管理员登录失败: loginName={}", request.getLoginName());
            throw BusinessException.of(ErrorCode.LOGIN_FAILED);
        }
        if (admin.getStatus() == null || admin.getStatus() != Constants.STATUS_ENABLED) {
            throw BusinessException.of(ErrorCode.ACCOUNT_DISABLED);
        }

        LoginUser user = LoginUser.builder()
                .userId(admin.getId())
                .userType(Constants.USER_TYPE_ADMIN)
                .loginName(admin.getLoginName())
                .build();
        String token = jwtUtil.createToken(user);

        // 登录时间更新属于"尽力而为"，失败不应影响登录本身
        try {
            adminMapper.updateLastLoginTime(admin.getId());
        } catch (Exception e) {
            log.warn("更新管理员登录时间失败: id={} err={}", admin.getId(), e.getMessage());
        }

        return AdminLoginVO.builder()
                .token(token)
                .userId(admin.getId())
                .userType(Constants.USER_TYPE_ADMIN)
                .loginName(admin.getLoginName())
                .adminName(admin.getAdminName())
                .expiresIn(jwtUtil.expireSeconds())
                .build();
    }

    /**
     * 当前管理员信息（接口 4.2）。
     */
    public AdminInfoVO adminInfo(LoginUser user) {
        SysAdmin admin = adminMapper.selectById(user.getUserId());
        if (admin == null) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED);
        }
        return AdminInfoVO.builder()
                .userId(admin.getId())
                .loginName(admin.getLoginName())
                .adminName(admin.getAdminName())
                .phone(admin.getPhone())
                .email(admin.getEmail())
                .lastLoginTime(admin.getLastLoginTime())
                .build();
    }

    // ------------------------------------------------------------------
    // 流通节点端
    // ------------------------------------------------------------------

    /**
     * 企业登录（接口 4.4）。
     */
    public EnterpriseLoginVO enterpriseLogin(LoginRequest request) {
        NodeEnterprise enterprise = enterpriseMapper.selectOne(Wrappers.<NodeEnterprise>lambdaQuery()
                .eq(NodeEnterprise::getLoginName, request.getLoginName())
                .last("LIMIT 1"));

        if (enterprise == null || !passwordEncoder.matches(request.getPassword(), enterprise.getPassword())) {
            log.warn("企业登录失败: loginName={}", request.getLoginName());
            throw BusinessException.of(ErrorCode.LOGIN_FAILED);
        }
        if (enterprise.getStatus() == null || enterprise.getStatus() != Constants.STATUS_ENABLED) {
            throw BusinessException.of(ErrorCode.ACCOUNT_DISABLED);
        }

        LoginUser user = LoginUser.builder()
                .userId(enterprise.getId())
                .userType(Constants.USER_TYPE_ENTERPRISE)
                .enterpriseId(enterprise.getId())
                .enterpriseType(enterprise.getEnterpriseType())
                .enterpriseName(enterprise.getEnterpriseName())
                .loginName(enterprise.getLoginName())
                .build();
        String token = jwtUtil.createToken(user);

        try {
            enterpriseMapper.update(null, Wrappers.<NodeEnterprise>lambdaUpdate()
                    .eq(NodeEnterprise::getId, enterprise.getId())
                    .set(NodeEnterprise::getLastLoginTime, LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("更新企业登录时间失败: id={} err={}", enterprise.getId(), e.getMessage());
        }

        // 登录成功后刷新状态缓存，避免刚启用/停用的企业在 60 秒窗口内表现异常
        enterpriseStatusChecker.evict(enterprise.getId());

        return EnterpriseLoginVO.builder()
                .token(token)
                .enterpriseId(enterprise.getId())
                .enterpriseCode(enterprise.getEnterpriseCode())
                .enterpriseName(enterprise.getEnterpriseName())
                .enterpriseType(enterprise.getEnterpriseType())
                .enterpriseTypeName(dictService.enterpriseTypeName(enterprise.getEnterpriseType()))
                .provinceName(enterprise.getProvinceName())
                .cityName(enterprise.getCityName())
                .expiresIn(jwtUtil.expireSeconds())
                .build();
    }

    /**
     * 当前登录企业信息（接口 4.5）。与 8.1 同源，结构一致。
     */
    public EnterpriseInfoVO enterpriseInfo(LoginUser user) {
        NodeEnterprise enterprise = enterpriseMapper.selectById(user.getEnterpriseId());
        if (enterprise == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND, "企业信息不存在");
        }
        return enterpriseConverter.toInfo(enterprise, EDITABLE_FIELDS);
    }

    /**
     * 修改本企业密码（接口 4.6）。
     *
     * <p>成功后把当前令牌加入黑名单，强制重新登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(LoginUser user, ChangePasswordRequest request, String rawToken) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "两次输入的新密码不一致");
        }
        NodeEnterprise enterprise = enterpriseMapper.selectById(user.getEnterpriseId());
        if (enterprise == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND, "企业信息不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), enterprise.getPassword())) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "原密码不正确");
        }
        if (passwordEncoder.matches(request.getNewPassword(), enterprise.getPassword())) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "新密码不能与原密码相同");
        }

        enterpriseMapper.update(null, Wrappers.<NodeEnterprise>lambdaUpdate()
                .eq(NodeEnterprise::getId, enterprise.getId())
                .set(NodeEnterprise::getPassword, passwordEncoder.encode(request.getNewPassword())));

        invalidateToken(rawToken, user);
        log.info("企业修改密码成功: enterpriseId={}", enterprise.getId());
    }

    // ------------------------------------------------------------------
    // 登出
    // ------------------------------------------------------------------

    /**
     * 退出登录（接口 4.3 / 4.7）。
     *
     * <p>无状态 JWT 无法服务端销毁，这里将其加入黑名单，
     * 有效期等于该令牌的剩余生存时间。
     */
    public void logout(LoginUser user, String rawToken) {
        invalidateToken(rawToken, user);
    }

    private void invalidateToken(String rawToken, LoginUser user) {
        if (rawToken == null || rawToken.isBlank() || user == null || user.getTokenId() == null) {
            return;
        }
        tokenBlacklist.add(user.getTokenId(), jwtUtil.remainSeconds(user));
    }
}
