package com.fsts.trace.controller;

import com.fsts.trace.common.Result;
import com.fsts.trace.service.TraceChainService;
import com.fsts.trace.vo.PublicTraceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费者端溯源查询（接口 12.1）。
 *
 * <p>免登录 + 限流（见 RateLimitInterceptor）：这是全系统唯一对公网完全开放的写-读混合接口，
 * 也是最容易被脚本刷的入口，必须在最外层做流量保护。
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicTraceController {

    private final TraceChainService traceChainService;

    /**
     * 12.1 按溯源标识码查询溯源信息。
     */
    @GetMapping("/trace/{traceCode}")
    public Result<PublicTraceVO> trace(@PathVariable("traceCode") String traceCode) {
        return Result.ok("查询成功", traceChainService.query(traceCode));
    }
}
