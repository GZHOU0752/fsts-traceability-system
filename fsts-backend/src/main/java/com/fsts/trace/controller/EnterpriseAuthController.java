package com.fsts.trace.controller;

import com.fsts.trace.common.LoginUser;
import com.fsts.trace.common.Result;
import com.fsts.trace.common.annotation.CurrentUser;
import com.fsts.trace.dto.request.ChangePasswordRequest;
import com.fsts.trace.dto.request.LoginRequest;
import com.fsts.trace.service.AuthService;
import com.fsts.trace.vo.EnterpriseInfoVO;
import com.fsts.trace.vo.EnterpriseLoginVO;
import com.fsts.trace.web.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流通节点端认证与本企业信息（接口 4.4 ~ 4.7、8.1）。
 */
@RestController
@RequestMapping("/api/enterprise")
@RequiredArgsConstructor
public class EnterpriseAuthController {

    private final AuthService authService;

    /**
     * 4.4 节点企业登录（匿名）。
     */
    @PostMapping("/auth/login")
    public Result<EnterpriseLoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok("登录成功", authService.enterpriseLogin(request));
    }

    /**
     * 4.5 获取当前登录企业信息。
     */
    @GetMapping("/auth/info")
    public Result<EnterpriseInfoVO> info(@CurrentUser LoginUser user) {
        return Result.ok("查询成功", authService.enterpriseInfo(user));
    }

    /**
     * 4.6 修改本企业密码。
     *
     * <p>成功后当前令牌立即失效，需要重新登录。
     */
    @PutMapping("/auth/password")
    public Result<Void> changePassword(@CurrentUser LoginUser user,
                                       @Valid @RequestBody ChangePasswordRequest request,
                                       HttpServletRequest servletRequest) {
        authService.changePassword(user, request, (String) servletRequest.getAttribute(AuthInterceptor.ATTR_TOKEN));
        return Result.ok("密码修改成功，请重新登录");
    }

    /**
     * 4.7 节点企业退出登录。
     */
    @PostMapping("/auth/logout")
    public Result<Void> logout(@CurrentUser LoginUser user, HttpServletRequest request) {
        authService.logout(user, (String) request.getAttribute(AuthInterceptor.ATTR_TOKEN));
        return Result.ok("退出成功");
    }

    /**
     * 8.1 查询本企业信息（与 4.5 同源，前端可复用同一数据模型）。
     */
    @GetMapping("/profile")
    public Result<EnterpriseInfoVO> profile(@CurrentUser LoginUser user) {
        return Result.ok("查询成功", authService.enterpriseInfo(user));
    }
}
