package com.fsts.trace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.Constants;
import com.fsts.trace.common.ErrorCode;
import com.fsts.trace.common.ProvinceAbbr;
import com.fsts.trace.config.props.BatchProperties;
import com.fsts.trace.config.props.TraceProperties;
import com.fsts.trace.entity.BatchRetail;
import com.fsts.trace.entity.ProductBatch;
import com.fsts.trace.entity.TraceCode;
import com.fsts.trace.mapper.TraceCodeMapper;
import com.fsts.trace.support.sequence.SequenceService;
import com.fsts.trace.vo.PublicProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 溯源标识码服务（接口 10.10 / 11.3 / 12.1）。
 *
 * <p>编码规则：FSTS-yyyyMMdd-{省简称拼音首字母}-{4 位流水}，例如 FSTS-20260915-SH-0001。
 *
 * <p>并发安全的三道防线：
 * <ol>
 *   <li>{@code sys_sequence} 原子取号，保证同一"日期 + 省份"维度流水不重复；</li>
 *   <li>{@code uk_trace_batch} 唯一索引保证"一个批号只生成一个标识码"；</li>
 *   <li>冲突时有限次重试，且重试前先查询是否已生成（幂等），避免重复插入。</li>
 * </ol>
 */
@Slf4j
@Service
public class TraceCodeService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TraceCodeMapper traceCodeMapper;
    private final SequenceService sequenceService;
    private final TraceProperties traceProperties;
    private final BatchProperties batchProperties;

    public TraceCodeService(TraceCodeMapper traceCodeMapper,
                            SequenceService sequenceService,
                            TraceProperties traceProperties,
                            BatchProperties batchProperties) {
        this.traceCodeMapper = traceCodeMapper;
        this.sequenceService = sequenceService;
        this.traceProperties = traceProperties;
        this.batchProperties = batchProperties;
    }

    /**
     * 为零售商批号生成溯源标识码。
     *
     * <p>调用前必须保证批号状态已置为 3，且与批号状态变更处于同一事务，
     * 生成失败时整体回滚（接口 11.3 业务规则 4）。
     *
     * @return 已存在则直接返回既有标识码（幂等）
     */
    public TraceCode generateForBatch(ProductBatch batch, BatchRetail retail, String retailerProvinceCode) {
        TraceCode existing = getByBatchId(batch.getId());
        if (existing != null) {
            log.debug("批号已存在溯源标识码，直接返回: batchId={} code={}", batch.getId(), existing.getTraceCode());
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        String datePart = now.format(DATE_FORMATTER);
        // 溯源码中的省份取"零售商所在省"，因此由调用方显式传入，避免误用上游省份
        String abbr = ProvinceAbbr.of(retailerProvinceCode);

        int maxRetry = Math.max(batchProperties.getTraceCodeMaxRetry(), 1);
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            long seq = sequenceService.next("trace:" + datePart + ":" + abbr);
            String code = Constants.TRACE_CODE_PREFIX + datePart + "-" + abbr + "-" + String.format("%04d", seq);

            TraceCode entity = new TraceCode();
            entity.setTraceCode(code);
            entity.setBatchId(batch.getId());
            entity.setBatchNo(batch.getBatchNo());
            entity.setRetailerId(batch.getEnterpriseId());
            entity.setRetailerName(batch.getEnterpriseName());
            entity.setProductVariety(batch.getProductVariety());
            entity.setSaleStore(retail == null ? null : retail.getSaleStore());
            entity.setQrContent(traceProperties.getQrBaseUrl() + code);
            entity.setStatus(1);
            entity.setQueryCount(0);
            entity.setGenerateTime(now);

            try {
                traceCodeMapper.insert(entity);
                return entity;
            } catch (DuplicateKeyException e) {
                // 并发下另一种可能：别的线程已为该批号生成了标识码，直接复用
                TraceCode concurrent = getByBatchId(batch.getId());
                if (concurrent != null) {
                    return concurrent;
                }
                log.warn("溯源码生成冲突，第 {} 次重试: code={}", attempt, code);
            }
        }
        throw BusinessException.of(ErrorCode.CONFLICT, "溯源标识码生成失败，请重试");
    }

    /**
     * 按批号查询标识码。
     */
    public TraceCode getByBatchId(Long batchId) {
        return traceCodeMapper.selectOne(Wrappers.<TraceCode>lambdaQuery()
                .eq(TraceCode::getBatchId, batchId)
                .last("LIMIT 1"));
    }

    /**
     * 按标识码查询（消费者端入口，走 uk_trace_code 唯一索引）。
     */
    public TraceCode getByTraceCode(String traceCode) {
        return traceCodeMapper.selectByTraceCode(traceCode);
    }

    /**
     * 批号下架时同步失效标识码（接口 10.7 业务规则 2）。
     */
    public void invalidateByBatchId(Long batchId) {
        int affected = traceCodeMapper.invalidateByBatchId(batchId);
        if (affected > 0) {
            log.info("溯源标识码已失效: batchId={}", batchId);
        }
    }

    /**
     * 消费者端产品搜索：返回可溯源的已发布产品列表。
     */
    public List<PublicProductVO> searchProducts(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        List<TraceCode> list = traceCodeMapper.searchProducts(kw);
        List<PublicProductVO> result = new ArrayList<>(list.size());
        for (TraceCode item : list) {
            result.add(PublicProductVO.builder()
                    .traceCode(item.getTraceCode())
                    .batchNo(item.getBatchNo())
                    .productVariety(item.getProductVariety())
                    .retailerName(item.getRetailerName())
                    .saleStore(item.getSaleStore())
                    .generateTime(item.getGenerateTime())
                    .build());
        }
        return result;
    }
}
