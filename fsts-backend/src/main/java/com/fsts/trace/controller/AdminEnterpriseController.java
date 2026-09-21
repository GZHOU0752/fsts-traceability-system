package com.fsts.trace.controller;

import com.fsts.trace.common.Result;
import com.fsts.trace.common.annotation.Idempotent;
import com.fsts.trace.dto.query.AdminEnterpriseQuery;
import com.fsts.trace.dto.request.EnterpriseCreateRequest;
import com.fsts.trace.dto.request.EnterpriseUpdateRequest;
import com.fsts.trace.service.AdminEnterpriseService;
import com.fsts.trace.vo.CheckAvailableVO;
import com.fsts.trace.vo.EnterpriseCreateResultVO;
import com.fsts.trace.vo.EnterpriseDetailVO;
import com.fsts.trace.vo.EnterpriseListItemVO;
import com.fsts.trace.vo.PageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 系统管理端 - 节点企业注册信息管理（接口 6.1 ~ 6.6）。
 */
@RestController
@RequestMapping("/api/admin/enterprises")
@RequiredArgsConstructor
public class AdminEnterpriseController {

    private final AdminEnterpriseService enterpriseService;

    /**
     * 6.1 分页与模糊查询节点企业。
     */
    @GetMapping
    public Result<PageVO<EnterpriseListItemVO>> page(AdminEnterpriseQuery query) {
        return Result.ok("查询成功", enterpriseService.page(query));
    }

    /**
     * 6.6 企业名称 / 登录账号 / 营业执照编号唯一性校验。
     *
     * <p>注意：必须声明在 {@code /{id}} 之前语义上更清晰；
     * Spring MVC 的路径匹配对字面量路径的优先级高于路径变量，因此不会与 6.2 冲突。
     */
    @GetMapping("/check-name")
    public Result<CheckAvailableVO> checkName(@RequestParam("field") String field,
                                              @RequestParam("value") String value,
                                              @RequestParam(value = "excludeId", required = false) Long excludeId) {
        return Result.ok("校验完成", enterpriseService.checkAvailable(field, value, excludeId));
    }

    /**
     * 6.2 节点企业详情。
     */
    @GetMapping("/{id}")
    public Result<EnterpriseDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok("查询成功", enterpriseService.detail(id));
    }

    /**
     * 6.3 新建节点企业。
     */
    @Idempotent(ttlSeconds = 5, message = "企业注册请求正在处理中，请勿重复提交")
    @PostMapping
    public Result<EnterpriseCreateResultVO> create(@Valid @RequestBody EnterpriseCreateRequest request) {
        return Result.ok("新建成功", enterpriseService.create(request));
    }

    /**
     * 6.4 更新节点企业。
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody EnterpriseUpdateRequest request) {
        enterpriseService.update(id, request);
        return Result.ok("更新成功");
    }

    /**
     * 6.5 删除节点企业（逻辑删除）。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        enterpriseService.delete(id);
        return Result.ok("删除成功");
    }
}
