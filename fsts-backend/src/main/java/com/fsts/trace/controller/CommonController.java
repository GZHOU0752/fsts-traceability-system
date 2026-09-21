package com.fsts.trace.controller;

import com.fsts.trace.common.Result;
import com.fsts.trace.service.DictService;
import com.fsts.trace.service.RegionService;
import com.fsts.trace.vo.DictItemVO;
import com.fsts.trace.vo.RegionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公共基础数据接口（第 5 章）：省 / 市 / 数据字典。
 *
 * <p>三类接口全部匿名访问且走缓存，因此可以安全地承受高频调用；
 * 若前端在多个页面重复初始化，实际也不会产生数据库压力。
 */
@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class CommonController {

    private final RegionService regionService;
    private final DictService dictService;

    /**
     * 5.1 查询省列表。
     */
    @GetMapping("/regions/provinces")
    public Result<List<RegionVO>> provinces() {
        return Result.ok("查询成功", regionService.listProvinces());
    }

    /**
     * 5.2 按省查询市列表。
     */
    @GetMapping("/regions/cities")
    public Result<List<RegionVO>> cities(@RequestParam("provinceCode") String provinceCode) {
        return Result.ok("查询成功", regionService.listCities(provinceCode));
    }

    /**
     * 5.3 查询字典项。
     */
    @GetMapping("/dicts/{typeCode}")
    public Result<List<DictItemVO>> dicts(@PathVariable("typeCode") String typeCode) {
        return Result.ok("查询成功", dictService.listByType(typeCode));
    }
}
