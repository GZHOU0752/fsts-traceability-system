package com.fsts.trace.controller;

import com.fsts.trace.common.LoginUser;
import com.fsts.trace.common.Result;
import com.fsts.trace.common.annotation.Idempotent;
import com.fsts.trace.dto.query.ConfirmRequestQuery;
import com.fsts.trace.dto.request.HandleRemarkRequest;
import com.fsts.trace.dto.request.RejectRequest;
import com.fsts.trace.service.ConfirmRequestService;
import com.fsts.trace.vo.ConfirmRequestDetailVO;
import com.fsts.trace.vo.ConfirmRequestListItemVO;
import com.fsts.trace.vo.ConfirmResultVO;
import com.fsts.trace.vo.PageVO;
import com.fsts.trace.vo.RejectResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流通节点端 - 下游企业进场确认（接口 11.1 ~ 11.4）。
 */
@RestController
@RequestMapping("/api/enterprise/confirm-requests")
@RequiredArgsConstructor
public class ConfirmRequestController {

    private final ConfirmRequestService confirmRequestService;

    /**
     * 11.1 待处理进场确认请求列表。
     */
    @GetMapping
    public Result<PageVO<ConfirmRequestListItemVO>> page(ConfirmRequestQuery query) {
        return Result.ok("查询成功", confirmRequestService.page(currentUser(), query));
    }

    /**
     * 11.2 确认请求详情。
     */
    @GetMapping("/{id}")
    public Result<ConfirmRequestDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok("查询成功", confirmRequestService.detail(currentUser(), id));
    }

    /**
     * 11.3 确认进场。
     */
    @Idempotent(ttlSeconds = 3, message = "确认操作正在处理中，请勿重复提交")
    @PutMapping("/{id}/confirm")
    public Result<ConfirmResultVO> confirm(@PathVariable("id") Long id,
                                           @RequestBody(required = false) HandleRemarkRequest request) {
        String remark = request == null ? null : request.getHandleRemark();
        return Result.ok("确认成功", confirmRequestService.confirm(currentUser(), id, remark));
    }

    /**
     * 11.4 拒绝进场（扩展功能）。
     */
    @Idempotent(ttlSeconds = 3)
    @PutMapping("/{id}/reject")
    public Result<RejectResultVO> reject(@PathVariable("id") Long id,
                                         @Valid @RequestBody RejectRequest request) {
        return Result.ok("已拒绝进场确认请求",
                confirmRequestService.reject(currentUser(), id, request.getHandleRemark()));
    }

    /**
     * 从上下文取当前登录企业（本控制器的所有接口都要求企业身份）。
     */
    private LoginUser currentUser() {
        return com.fsts.trace.common.UserContext.requireEnterprise();
    }
}
