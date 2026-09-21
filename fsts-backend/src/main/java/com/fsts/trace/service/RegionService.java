package com.fsts.trace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fsts.trace.config.props.CacheProperties;
import com.fsts.trace.entity.SysRegion;
import com.fsts.trace.mapper.SysRegionMapper;
import com.fsts.trace.support.cache.CacheService;
import com.fsts.trace.vo.RegionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 行政区划服务。
 *
 * <p>缓存策略：省级 34 条、市级上百条，属于"系统启动后基本不变"的静态数据，
 * 因此 TTL 设得较长（默认 24 小时），配合主动失效即可。
 * 省市下拉是前端每个表单页初始化都要调用的接口，缓存收益非常明显。
 */
@Slf4j
@Service
public class RegionService {

    private static final String NAMESPACE = "region";
    private static final String PROVINCE_KEY = "provinces";

    private final SysRegionMapper regionMapper;
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;

    public RegionService(SysRegionMapper regionMapper, CacheService cacheService, CacheProperties cacheProperties) {
        this.regionMapper = regionMapper;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
    }

    /**
     * 省列表（接口 5.1）。默认不设置初始选中项，由前端决定。
     */
    public List<RegionVO> listProvinces() {
        List<RegionVO> cached = cacheService.getList(NAMESPACE, PROVINCE_KEY, RegionVO.class);
        if (cached != null) {
            return cached;
        }
        List<SysRegion> provinces = regionMapper.selectList(Wrappers.<SysRegion>lambdaQuery()
                .eq(SysRegion::getRegionLevel, 1)
                .eq(SysRegion::getStatus, 1)
                .orderByAsc(SysRegion::getSortNo));

        List<RegionVO> result = new ArrayList<>(provinces.size());
        for (SysRegion region : provinces) {
            result.add(RegionVO.builder()
                    .regionCode(region.getRegionCode())
                    .regionName(region.getRegionName())
                    .shortName(region.getShortName())
                    .sortNo(region.getSortNo())
                    .build());
        }
        cacheService.put(NAMESPACE, PROVINCE_KEY, result, Duration.ofSeconds(cacheProperties.getRegionTtlSeconds()));
        return result;
    }

    /**
     * 按省查询市列表（接口 5.2）。
     */
    public List<RegionVO> listCities(String provinceCode) {
        String cacheKey = "cities:" + provinceCode;
        List<RegionVO> cached = cacheService.getList(NAMESPACE, cacheKey, RegionVO.class);
        if (cached != null) {
            return cached;
        }
        List<SysRegion> cities = regionMapper.selectList(Wrappers.<SysRegion>lambdaQuery()
                .eq(SysRegion::getParentCode, provinceCode)
                .eq(SysRegion::getRegionLevel, 2)
                .eq(SysRegion::getStatus, 1)
                .orderByAsc(SysRegion::getSortNo));

        List<RegionVO> result = new ArrayList<>(cities.size());
        for (SysRegion region : cities) {
            result.add(RegionVO.builder()
                    .regionCode(region.getRegionCode())
                    .regionName(region.getRegionName())
                    .sortNo(region.getSortNo())
                    .build());
        }
        cacheService.put(NAMESPACE, cacheKey, result, Duration.ofSeconds(cacheProperties.getRegionTtlSeconds()));
        return result;
    }

    /**
     * 取省名称（新建/更新企业时后端回填使用）。
     */
    public String provinceName(String provinceCode) {
        for (RegionVO province : listProvinces()) {
            if (province.getRegionCode().equals(provinceCode)) {
                return province.getRegionName();
            }
        }
        return null;
    }

    /**
     * 取市名称（新建/更新企业时后端回填使用）。
     */
    public String cityName(String provinceCode, String cityCode) {
        for (RegionVO city : listCities(provinceCode)) {
            if (city.getRegionCode().equals(cityCode)) {
                return city.getRegionName();
            }
        }
        return null;
    }

    /**
     * 主动失效区划缓存。
     */
    public void evictAll() {
        cacheService.evictNamespace(NAMESPACE);
    }
}
