package com.fsts.trace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fsts.trace.config.props.CacheProperties;
import com.fsts.trace.entity.SysDictItem;
import com.fsts.trace.mapper.SysDictItemMapper;
import com.fsts.trace.support.cache.CacheService;
import com.fsts.trace.vo.DictItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 数据字典服务。
 *
 * <p>缓存策略：字典是典型的"读极多、写极少"数据，每次请求直连数据库会浪费大量连接；
 * 因此按 typeCode 整体缓存，命中时零数据库访问。字典变更时调用 evict 主动失效。
 */
@Slf4j
@Service
public class DictService {

    private static final String NAMESPACE = "dict";

    private final SysDictItemMapper dictItemMapper;
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;

    public DictService(SysDictItemMapper dictItemMapper, CacheService cacheService, CacheProperties cacheProperties) {
        this.dictItemMapper = dictItemMapper;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
    }

    /**
     * 查询字典项（接口 5.3）：只返回启用项，按 sortNo 升序。
     */
    public List<DictItemVO> listByType(String typeCode) {
        List<DictItemVO> cached = cacheService.getList(NAMESPACE, typeCode, DictItemVO.class);
        if (cached != null) {
            return cached;
        }
        List<SysDictItem> items = dictItemMapper.selectList(Wrappers.<SysDictItem>lambdaQuery()
                .eq(SysDictItem::getTypeCode, typeCode)
                .eq(SysDictItem::getStatus, 1)
                .orderByAsc(SysDictItem::getSortNo));

        List<DictItemVO> result = new ArrayList<>(items.size());
        for (SysDictItem item : items) {
            result.add(DictItemVO.builder()
                    .itemCode(item.getItemCode())
                    .itemValue(item.getItemValue())
                    .sortNo(item.getSortNo())
                    .build());
        }
        cacheService.put(NAMESPACE, typeCode, result, Duration.ofSeconds(cacheProperties.getDictTtlSeconds()));
        return result;
    }

    /**
     * 按编码取字典名称（如 batchStatus=3 得到"已确认"）。
     *
     * @return 未匹配到时返回 null，由调用方决定兜底文案
     */
    public String nameOf(String typeCode, Object itemCode) {
        if (itemCode == null) {
            return null;
        }
        String code = String.valueOf(itemCode);
        for (DictItemVO item : listByType(typeCode)) {
            if (code.equals(item.getItemCode())) {
                return item.getItemValue();
            }
        }
        return null;
    }

    /** 企业类型名称：1 得到 捕捞与养殖企业 */
    public String enterpriseTypeName(Integer enterpriseType) {
        return nameOf("enterprise_type", enterpriseType);
    }

    /** 批号状态名称：1 新建，2 待确认，3 已确认，4 已下架 */
    public String batchStatusName(Integer batchStatus) {
        return nameOf("batch_status", batchStatus);
    }

    /** 来源类型名称：1 养殖，2 捕捞 */
    public String sourceTypeName(Integer sourceType) {
        return nameOf("source_type", sourceType);
    }

    /** 证明材料类型名称 */
    public String certificateTypeName(Integer certificateType) {
        return nameOf("certificate_type", certificateType);
    }

    /** 确认请求状态名称 */
    public String requestStatusName(Integer requestStatus) {
        return nameOf("request_status", requestStatus);
    }

    /**
     * 判断字典值是否存在（用于校验加工形态、产品品种等候选值）。
     */
    public boolean containsValue(String typeCode, String itemValue) {
        if (itemValue == null) {
            return false;
        }
        for (DictItemVO item : listByType(typeCode)) {
            if (itemValue.equals(item.getItemValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 主动失效某类字典缓存（字典维护后调用）。
     */
    public void evict(String typeCode) {
        cacheService.evict(NAMESPACE, typeCode);
        log.info("字典缓存已失效: {}", typeCode);
    }

    /** 全部失效 */
    public void evictAll() {
        cacheService.evictNamespace(NAMESPACE);
    }

    /** 支持的字典类型清单（用于参数白名单校验） */
    public static List<String> supportedTypes() {
        return Collections.unmodifiableList(List.of(
                "enterprise_type", "source_type", "certificate_type",
                "process_form", "product_variety", "batch_status", "request_status"));
    }
}
