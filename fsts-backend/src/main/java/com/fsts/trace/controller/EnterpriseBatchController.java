package com.fsts.trace.controller;

import com.fsts.trace.common.LoginUser;
import com.fsts.trace.common.Result;
import com.fsts.trace.common.annotation.CurrentUser;
import com.fsts.trace.common.annotation.Idempotent;
import com.fsts.trace.dto.query.BatchQuery;
import com.fsts.trace.dto.query.UpstreamBatchQuery;
import com.fsts.trace.dto.query.UpstreamEnterpriseQuery;
import com.fsts.trace.dto.request.BatchSaveRequest;
import com.fsts.trace.dto.request.BatchUpdateRequest;
import com.fsts.trace.dto.request.OffShelfRequest;
import com.fsts.trace.service.ConfirmRequestService;
import com.fsts.trace.service.EnterpriseBatchService;
import com.fsts.trace.service.UpstreamService;
import com.fsts.trace.vo.BatchDetailVO;
import com.fsts.trace.vo.BatchListItemVO;
import com.fsts.trace.vo.BatchPublishResultVO;
import com.fsts.trace.vo.BatchSaveResultVO;
import com.fsts.trace.vo.CheckAvailableVO;
import com.fsts.trace.vo.ConfirmRequestSendVO;
import com.fsts.trace.vo.PageVO;
import com.fsts.trace.vo.TraceCodeInfoVO;
import com.fsts.trace.vo.UpstreamBatchVO;
import com.fsts.trace.vo.UpstreamEnterpriseVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流通节点端 - 上游数据选择与产品批号管理（接口 9.1 ~ 10.10）。
 */
@Validated
@RestController
@RequestMapping("/api/enterprise")
@RequiredArgsConstructor
public class EnterpriseBatchController {

    private final UpstreamService upstreamService;
    private final EnterpriseBatchService batchService;
    private final ConfirmRequestService confirmRequestService;

    // ---------------- 9 上游数据选择 ----------------

    /**
     * 9.1 查询上游企业列表。
     */
    @GetMapping("/upstream/enterprises")
    public Result<PageVO<UpstreamEnterpriseVO>> upstreamEnterprises(@CurrentUser LoginUser user,
                                                                   UpstreamEnterpriseQuery query) {
        return Result.ok("查询成功", upstreamService.listUpstreamEnterprises(user, query));
    }

    /**
     * 9.2 查询上游企业已发布产品批号。
     */
    @GetMapping("/upstream/batches")
    public Result<PageVO<UpstreamBatchVO>> upstreamBatches(@CurrentUser LoginUser user,
                                                           @Valid UpstreamBatchQuery query) {
        return Result.ok("查询成功", upstreamService.listUpstreamBatches(user, query));
    }

    // ---------------- 10 产品批号管理 ----------------

    /**
     * 10.1 分页查询本企业产品批号列表。
     */
    @GetMapping("/batches")
    public Result<PageVO<BatchListItemVO>> page(@CurrentUser LoginUser user, @Valid BatchQuery query) {
        return Result.ok("查询成功", batchService.page(user, query));
    }

    /**
     * 10.8 产品批号唯一性校验。
     *
     * <p>Spring MVC 对字面量路径的匹配优先级高于路径变量，故不会与 10.2 冲突。
     */
    @GetMapping("/batches/check-batch-no")
    public Result<CheckAvailableVO> checkBatchNo(@CurrentUser LoginUser user,
                                                 @RequestParam("batchNo") String batchNo,
                                                 @RequestParam(value = "excludeId", required = false) Long excludeId) {
        return Result.ok("校验完成", batchService.checkBatchNo(user, batchNo, excludeId));
    }

    /**
     * 10.2 查询产品批号详情。
     */
    @GetMapping("/batches/{id}")
    public Result<BatchDetailVO> detail(@CurrentUser LoginUser user, @PathVariable("id") Long id) {
        return Result.ok("查询成功", batchService.detail(user, id));
    }

    /**
     * 10.3 新建产品批号。
     */
    @Idempotent(ttlSeconds = 5, message = "批号创建请求正在处理中，请勿重复提交")
    @PostMapping("/batches")
    public Result<BatchSaveResultVO> create(@CurrentUser LoginUser user,
                                            @Valid @RequestBody BatchSaveRequest request) {
        return Result.ok("新建成功", batchService.create(user, request));
    }

    /**
     * 10.4 更新产品批号。
     */
    @PutMapping("/batches/{id}")
    public Result<BatchSaveResultVO> update(@CurrentUser LoginUser user,
                                            @PathVariable("id") Long id,
                                            @Valid @RequestBody BatchUpdateRequest request) {
        return Result.ok("更新成功", batchService.update(user, id, request));
    }

    /**
     * 10.5 删除产品批号。
     */
    @DeleteMapping("/batches/{id}")
    public Result<Void> delete(@CurrentUser LoginUser user, @PathVariable("id") Long id) {
        batchService.delete(user, id);
        return Result.ok("删除成功，删除后不可恢复");
    }

    /**
     * 10.6 发布产品批号（仅捕捞与养殖企业）。
     */
    @PutMapping("/batches/{id}/publish")
    public Result<BatchPublishResultVO> publish(@CurrentUser LoginUser user, @PathVariable("id") Long id) {
        return Result.ok("发布成功", batchService.publish(user, id));
    }

    /**
     * 10.7 下架产品批号。
     */
    @PutMapping("/batches/{id}/off-shelf")
    public Result<BatchPublishResultVO> offShelf(@CurrentUser LoginUser user,
                                                 @PathVariable("id") Long id,
                                                 @RequestBody(required = false) OffShelfRequest request) {
        return Result.ok("下架成功", batchService.offShelf(user, id, request));
    }

    /**
     * 10.9 向上游企业发送确认请求。
     */
    @Idempotent(ttlSeconds = 5)
    @PostMapping("/batches/{id}/confirm-request")
    public Result<ConfirmRequestSendVO> sendConfirmRequest(@CurrentUser LoginUser user,
                                                           @PathVariable("id") Long id) {
        return Result.ok("已向上游企业发送进场确认请求", confirmRequestService.send(user, id));
    }

    /**
     * 10.10 查询溯源标识码（仅零售商）。
     */
    @GetMapping("/batches/{id}/trace-code")
    public Result<TraceCodeInfoVO> traceCode(@CurrentUser LoginUser user, @PathVariable("id") Long id) {
        return Result.ok("查询成功", batchService.queryTraceCode(user, id));
    }
}
