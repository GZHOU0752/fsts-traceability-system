package com.fsts.trace.controller;

import com.fsts.trace.common.Result;
import com.fsts.trace.service.AdminStatisticsService;
import com.fsts.trace.vo.OverviewVO;
import com.fsts.trace.vo.ProvinceCountVO;
import com.fsts.trace.vo.ProvinceDistributionVO;
import com.fsts.trace.vo.RegisterTrendVO;
import com.fsts.trace.vo.TypeDistributionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统管理端 - 可视化大屏统计（接口 7.1 ~ 7.5）。
 *
 * <p>大屏初始化时会并行调用 7.1~7.5；
 * 7.3 与 7.4 数据源相同，前端可只调 7.3 后自行换算，后端仍保留两个接口以符合契约。
 */
@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
public class AdminStatisticsController {

    private final AdminStatisticsService statisticsService;

    /** 7.1 概览数据卡片 */
    @GetMapping("/overview")
    public Result<OverviewVO> overview() {
        return Result.ok("查询成功", statisticsService.overview());
    }

    /** 7.2 十二个月注册趋势（折线图） */
    @GetMapping("/register-trend")
    public Result<RegisterTrendVO> registerTrend(@RequestParam(value = "months", required = false) Integer months) {
        return Result.ok("查询成功", statisticsService.registerTrend(months));
    }

    /** 7.3 省分组分布（饼图） */
    @GetMapping("/province-distribution")
    public Result<List<ProvinceDistributionVO>> provinceDistribution(
            @RequestParam(value = "top", required = false) Integer top) {
        return Result.ok("查询成功", statisticsService.provinceDistribution(top));
    }

    /** 7.4 省分组数量（柱状图） */
    @GetMapping("/province-count")
    public Result<ProvinceCountVO> provinceCount() {
        return Result.ok("查询成功", statisticsService.provinceCount());
    }

    /** 7.5 企业类型分布（饼图） */
    @GetMapping("/type-distribution")
    public Result<List<TypeDistributionVO>> typeDistribution() {
        return Result.ok("查询成功", statisticsService.typeDistribution());
    }
}
