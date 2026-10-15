package com.fsts.trace.controller;

import com.fsts.trace.common.LoginUser;
import com.fsts.trace.common.Result;
import com.fsts.trace.common.annotation.CurrentUser;
import com.fsts.trace.dto.request.ChangePasswordRequest;
import com.fsts.trace.dto.request.LoginRequest;
import com.fsts.trace.service.AuthService;
import com.fsts.trace.vo.AdminInfoVO;
import com.fsts.trace.vo.AdminLoginVO;
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
 * 系统管理端认证（接口 4.1 ~ 4.3）。
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    /**
     * 4.1 系统管理员登录（匿名）。
     */
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok("登录成功", authService.adminLogin(request));
    }

    /**
     * 4.2 获取当前管理员信息。
     */
    @GetMapping("/info")
    public Result<AdminInfoVO> info(@CurrentUser LoginUser user) {
        return Result.ok("查询成功", authService.adminInfo(user));
    }

    /**
     * 4.3 管理员退出登录。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@CurrentUser LoginUser user, HttpServletRequest request) {
        authService.logout(user, (String) request.getAttribute(AuthInterceptor.ATTR_TOKEN));
        return Result.ok("退出成功");
    }

    /**
     * 4.8 修改管理员密码（成功后当前令牌失效，需重新登录）。
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@CurrentUser LoginUser user,
                                       @Valid @RequestBody ChangePasswordRequest request,
                                       HttpServletRequest servletRequest) {
        authService.adminChangePassword(user, request,
                (String) servletRequest.getAttribute(AuthInterceptor.ATTR_TOKEN));
        return Result.ok("密码修改成功，请重新登录");
    }
}
