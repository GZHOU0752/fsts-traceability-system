package com.fsts.trace.service;

import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.Constants;
import com.fsts.trace.common.ErrorCode;
import com.fsts.trace.common.util.TraceCodeCheckDigit;
import com.fsts.trace.config.props.TraceProperties;
import com.fsts.trace.entity.BatchFishing;
import com.fsts.trace.entity.BatchProcessing;
import com.fsts.trace.entity.BatchRetail;
import com.fsts.trace.entity.BatchWholesale;
import com.fsts.trace.entity.NodeEnterprise;
import com.fsts.trace.entity.ProductBatch;
import com.fsts.trace.entity.TraceCode;
import com.fsts.trace.mapper.BatchFishingMapper;
import com.fsts.trace.mapper.BatchProcessingMapper;
import com.fsts.trace.mapper.BatchRetailMapper;
import com.fsts.trace.mapper.BatchWholesaleMapper;
import com.fsts.trace.mapper.NodeEnterpriseMapper;
import com.fsts.trace.mapper.ProductBatchMapper;
import com.fsts.trace.service.support.ColdChainService;
import com.fsts.trace.support.cache.CacheService;
import com.fsts.trace.support.counter.TraceQueryCounter;
import com.fsts.trace.vo.CertificateVO;
import com.fsts.trace.vo.PublicTraceVO;
import com.fsts.trace.vo.TemperatureItemVO;
import com.fsts.trace.vo.TemperaturePointVO;
import com.fsts.trace.vo.TraceLinkVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费者端 - 溯源链组装（接口 12.1）。
 *
 * <p><b>性能设计：</b>
 * 1. SQL 次数上限固定：1 次标识码 + 1 次递归链 + 1 次企业 + 至多 4 次明细，
 *    与链条长度无关，不存在 N+1；
 * 2. 明细表按实际出现的环节按需查询：只有捕捞环节的批次不再为加工/批发/零售
 *    三张表各发一次空查询（单品链路由 6 次降到 3 次）；确实存在时按 batch_id
 *    批量 IN 查询，命中唯一索引；
 * 3. 查询次数不在此处写库，而是交给 {@link TraceQueryCounter} 内存聚合，
 *    读路径完全无写操作。
 *
 * <p><b>隐私设计：</b>只输出企业名称与所在地，不返回联系人、联系电话、登录账号。
 */
@Slf4j
@Service
public class TraceChainService {

    private static final String[] STAGE_NAMES = {
            "", "捕捞与养殖环节", "冷冻加工环节", "批发与冷链仓储环节", "零售环节"
    };

    /** 溯源结果快照的缓存命名空间 */
    private static final String CACHE_NAMESPACE = "trace";

    private final TraceCodeService traceCodeService;
    private final ProductBatchMapper batchMapper;
    private final BatchFishingMapper fishingMapper;
    private final BatchProcessingMapper processingMapper;
    private final BatchWholesaleMapper wholesaleMapper;
    private final BatchRetailMapper retailMapper;
    private final NodeEnterpriseMapper enterpriseMapper;
    private final DictService dictService;
    private final TraceQueryCounter queryCounter;
    private final TraceProperties traceProperties;
    private final CacheService cacheService;

    public TraceChainService(TraceCodeService traceCodeService,
                             ProductBatchMapper batchMapper,
                             BatchFishingMapper fishingMapper,
                             BatchProcessingMapper processingMapper,
                             BatchWholesaleMapper wholesaleMapper,
                             BatchRetailMapper retailMapper,
                             NodeEnterpriseMapper enterpriseMapper,
                             DictService dictService,
                             TraceQueryCounter queryCounter,
                             TraceProperties traceProperties,
                             CacheService cacheService) {
        this.traceCodeService = traceCodeService;
        this.batchMapper = batchMapper;
        this.fishingMapper = fishingMapper;
        this.processingMapper = processingMapper;
        this.wholesaleMapper = wholesaleMapper;
        this.retailMapper = retailMapper;
        this.enterpriseMapper = enterpriseMapper;
        this.dictService = dictService;
        this.queryCounter = queryCounter;
        this.traceProperties = traceProperties;
        this.cacheService = cacheService;
    }

    /**
     * 按溯源标识码查询溯源信息（接口 12.1）。
     *
     * <p>查询次数必须每次实时自增并回填，因此"标识码 + 计数"这一段永远走数据库；
     * 真正昂贵的"递归链 + 明细装配"结果放在 {@link #loadSnapshot} 里做短 TTL 缓存。
     */
    public PublicTraceVO query(String traceCodeValue) {
        String normalized = TraceCodeCheckDigit.assertValid(traceCodeValue, traceProperties.isCheckDigitEnabled());
        TraceCode traceCode = traceCodeService.getByTraceCode(normalized);
        if (traceCode == null) {
            throw BusinessException.of(ErrorCode.TRACE_CODE_NOT_FOUND);
        }
        if (traceCode.getStatus() == null || traceCode.getStatus() != 1) {
            throw BusinessException.of(ErrorCode.TRACE_CODE_INVALID);
        }

        TraceChainSnapshot snapshot = loadSnapshot(traceCode);

        // 读路径只做内存自增，真正的 UPDATE 由定时任务批量完成
        queryCounter.record(traceCode.getId());

        return PublicTraceVO.builder()
                .traceCode(traceCode.getTraceCode())
                .batchNo(traceCode.getBatchNo())
                .productVariety(traceCode.getProductVariety())
                .retailerName(snapshot.retailerName() != null
                        ? snapshot.retailerName() : traceCode.getRetailerName())
                .saleStore(traceCode.getSaleStore())
                .generateTime(traceCode.getGenerateTime())
                .queryCount((int) queryCounter.currentCount(traceCode.getId(), traceCode.getQueryCount()))
                .coldChainQualified(snapshot.coldChainQualified())
                .coldChainConclusion(snapshot.coldChainConclusion())
                .temperatureCurve(snapshot.temperatureCurve())
                .links(snapshot.links())
                .build();
    }

    /**
     * 取溯源链组装结果（带短 TTL 缓存）。
     *
     * <p>缓存只存"链路快照"（环节、温度、结论、零售商名称），不含查询次数，
     * 因为查询次数要求实时。批号下架/恢复上架会改变标识码状态，
     * 由 {@link TraceCodeService} 主动失效，避免出现"已下架还能查到"。
     */
    private TraceChainSnapshot loadSnapshot(TraceCode traceCode) {
        int ttl = traceProperties.getResultTtlSeconds();
        if (ttl <= 0) {
            return buildSnapshot(traceCode);
        }
        TraceChainSnapshot cached = cacheService.get(CACHE_NAMESPACE, traceCode.getTraceCode(), TraceChainSnapshot.class);
        if (cached != null) {
            return cached;
        }
        TraceChainSnapshot built = buildSnapshot(traceCode);
        cacheService.put(CACHE_NAMESPACE, traceCode.getTraceCode(), built, Duration.ofSeconds(ttl));
        return built;
    }

    /** 递归组装溯源链（原查询逻辑），结果为可缓存的不可变快照 */
    private TraceChainSnapshot buildSnapshot(TraceCode traceCode) {
        List<ProductBatch> chain = batchMapper.selectTraceChain(traceCode.getBatchId());
        if (chain == null || chain.isEmpty()) {
            throw BusinessException.of(ErrorCode.TRACE_CODE_NOT_FOUND);
        }
        // 递归结果按环节升序返回：1 捕捞与养殖 -> 2 冷冻加工 -> 3 批发 -> 4 零售
        chain.sort((a, b) -> Integer.compare(
                a.getEnterpriseType() == null ? 0 : a.getEnterpriseType(),
                b.getEnterpriseType() == null ? 0 : b.getEnterpriseType()));

        List<Long> batchIds = new ArrayList<>(chain.size());
        boolean hasFishing = false;
        boolean hasProcessing = false;
        boolean hasWholesale = false;
        boolean hasRetail = false;
        for (ProductBatch batch : chain) {
            batchIds.add(batch.getId());
            Integer type = batch.getEnterpriseType();
            if (type == null) {
                continue;
            }
            switch (type) {
                case Constants.ENTERPRISE_TYPE_FISHING -> hasFishing = true;
                case Constants.ENTERPRISE_TYPE_PROCESSING -> hasProcessing = true;
                case Constants.ENTERPRISE_TYPE_WHOLESALE -> hasWholesale = true;
                case Constants.ENTERPRISE_TYPE_RETAIL -> hasRetail = true;
                default -> {
                    // 未知环节没有对应明细表
                }
            }
        }
        Map<Long, BatchFishing> fishingMap = hasFishing ? indexFishing(batchIds) : Collections.emptyMap();
        Map<Long, BatchProcessing> processingMap = hasProcessing ? indexProcessing(batchIds) : Collections.emptyMap();
        Map<Long, BatchWholesale> wholesaleMap = hasWholesale ? indexWholesale(batchIds) : Collections.emptyMap();
        Map<Long, BatchRetail> retailMap = hasRetail ? indexRetail(batchIds) : Collections.emptyMap();
        Map<Long, NodeEnterprise> enterpriseMap = indexEnterprise(chain);

        List<TraceLinkVO> links = new ArrayList<>(chain.size());
        List<TemperaturePointVO> curve = new ArrayList<>(chain.size());
        List<String> abnormalStages = new ArrayList<>();

        for (ProductBatch batch : chain) {
            Integer type = batch.getEnterpriseType();
            NodeEnterprise enterprise = enterpriseMap.get(batch.getEnterpriseId());

            List<CertificateVO> certificates = new ArrayList<>(3);
            List<TemperatureItemVO> temperatures = new ArrayList<>(3);
            BigDecimal curveTemp = batch.getHandoverTemp();

            if (type != null) {
                switch (type) {
                    case Constants.ENTERPRISE_TYPE_FISHING -> {
                        BatchFishing detail = fishingMap.get(batch.getId());
                        if (detail != null) {
                            certificates.add(new CertificateVO(
                                    dictService.certificateTypeName(detail.getCertificateType()),
                                    detail.getCertificateNo()));
                            if (detail.getDrugReportNo() != null) {
                                certificates.add(new CertificateVO("水产品药物残留检测报告", detail.getDrugReportNo()));
                            }
                            temperatures.add(new TemperatureItemVO("起运温度", detail.getDepartureTemp(),
                                    traceProperties.getNormalTempThreshold(),
                                    ColdChainService.isQualified(detail.getDepartureTemp(), traceProperties.getNormalTempThreshold())));
                        }
                    }
                    case Constants.ENTERPRISE_TYPE_PROCESSING -> {
                        BatchProcessing detail = processingMap.get(batch.getId());
                        if (detail != null) {
                            certificates.add(new CertificateVO("出厂检验报告", detail.getInspectionNo()));
                            temperatures.add(new TemperatureItemVO("速冻中心温度", detail.getQuickFreezeTemp(),
                                    traceProperties.getQuickFreezeTempThreshold(),
                                    ColdChainService.isQualified(detail.getQuickFreezeTemp(), traceProperties.getQuickFreezeTempThreshold())));
                            temperatures.add(new TemperatureItemVO("出厂温度", detail.getFactoryTemp(),
                                    traceProperties.getNormalTempThreshold(),
                                    ColdChainService.isQualified(detail.getFactoryTemp(), traceProperties.getNormalTempThreshold())));
                            curveTemp = detail.getFactoryTemp();
                        }
                    }
                    case Constants.ENTERPRISE_TYPE_WHOLESALE -> {
                        BatchWholesale detail = wholesaleMap.get(batch.getId());
                        if (detail != null) {
                            if (detail.getColdStorageNo() != null) {
                                certificates.add(new CertificateVO("冷库编号", detail.getColdStorageNo()));
                            }
                            if (detail.getTransportToolNo() != null) {
                                certificates.add(new CertificateVO("运输工具编号", detail.getTransportToolNo()));
                            }
                            temperatures.add(new TemperatureItemVO("入库温度", detail.getInboundTemp(),
                                    traceProperties.getNormalTempThreshold(),
                                    ColdChainService.isQualified(detail.getInboundTemp(), traceProperties.getNormalTempThreshold())));
                            temperatures.add(new TemperatureItemVO("冷库温度", detail.getColdStorageTemp(),
                                    traceProperties.getNormalTempThreshold(),
                                    ColdChainService.isQualified(detail.getColdStorageTemp(), traceProperties.getNormalTempThreshold())));
                            temperatures.add(new TemperatureItemVO("出库温度", detail.getOutboundTemp(),
                                    traceProperties.getNormalTempThreshold(),
                                    ColdChainService.isQualified(detail.getOutboundTemp(), traceProperties.getNormalTempThreshold())));
                            curveTemp = detail.getOutboundTemp();
                        }
                    }
                    case Constants.ENTERPRISE_TYPE_RETAIL -> {
                        BatchRetail detail = retailMap.get(batch.getId());
                        if (detail != null) {
                            temperatures.add(new TemperatureItemVO("冷冻陈列柜温度", detail.getDisplayTemp(),
                                    traceProperties.getNormalTempThreshold(),
                                    ColdChainService.isQualified(detail.getDisplayTemp(), traceProperties.getNormalTempThreshold())));
                            curveTemp = detail.getDisplayTemp();
                        }
                    }
                    default -> {
                        // 未知环节不输出明细
                    }
                }
            }

            boolean stageOk = batch.getColdChainOk() == null || batch.getColdChainOk() == 1;
            if (!stageOk) {
                abnormalStages.add(stageName(type));
            }

            links.add(TraceLinkVO.builder()
                    .stageCode(type)
                    .stageName(stageName(type))
                    .enterpriseId(batch.getEnterpriseId())
                    .enterpriseName(batch.getEnterpriseName())
                    .enterpriseTypeName(dictService.enterpriseTypeName(type))
                    .provinceName(enterprise == null ? null : enterprise.getProvinceName())
                    .cityName(enterprise == null ? null : enterprise.getCityName())
                    .batchNo(batch.getBatchNo())
                    .upstreamBatchNo(batch.getUpstreamBatchNo())
                    .productVariety(batch.getProductVariety())
                    .sourceTypeName(dictService.sourceTypeName(batch.getSourceType()))
                    .handoverTemp(curveTemp)
                    .coldChainOk(stageOk)
                    .handoverTime(batch.getPublishTime())
                    .certificates(certificates)
                    .temperatures(temperatures)
                    .build());

            curve.add(TemperaturePointVO.builder()
                    .stageCode(type)
                    .stageName(stageName(type))
                    .enterpriseName(batch.getEnterpriseName())
                    .temperature(curveTemp)
                    .threshold(traceProperties.getNormalTempThreshold())
                    .qualified(stageOk && ColdChainService.isQualified(curveTemp, traceProperties.getNormalTempThreshold()))
                    .recordTime(batch.getPublishTime())
                    .build());
        }

        boolean coldChainQualified = abnormalStages.isEmpty();
        NodeEnterprise retailer = enterpriseMap.get(traceCode.getRetailerId());

        return new TraceChainSnapshot(
                retailer == null ? null : retailer.getEnterpriseName(),
                coldChainQualified,
                buildConclusion(chain.size(), coldChainQualified, abnormalStages),
                List.copyOf(curve),
                List.copyOf(links));
    }

    /**
     * 溯源链快照（缓存单元）。
     *
     * <p>用 record 而非 Map：字段语义明确，且 Jackson 2.12+ 原生支持 record 的
     * 规范构造器反序列化，不需要像普通 VO 那样担心"@Builder 吃掉无参构造器"。
     */
    public record TraceChainSnapshot(
            String retailerName,
            boolean coldChainQualified,
            String coldChainConclusion,
            List<TemperaturePointVO> temperatureCurve,
            List<TraceLinkVO> links) {
    }

    /**
     * 冷链结论文案（接口 12.1 业务规则 5）。
     */
    private String buildConclusion(int stageCount, boolean qualified, List<String> abnormalStages) {
        if (qualified) {
            return "本批次自捕捞与养殖环节至零售环节共 " + stageCount
                    + " 个环节温度记录全部合格，冷链未断链";
        }
        return "环节「" + String.join("、", abnormalStages) + "」温度记录不合格，存在冷链断链风险";
    }

    private String stageName(Integer enterpriseType) {
        if (enterpriseType == null || enterpriseType < 1 || enterpriseType >= STAGE_NAMES.length) {
            return "未知环节";
        }
        return STAGE_NAMES[enterpriseType];
    }

    private Map<Long, BatchFishing> indexFishing(List<Long> batchIds) {
        List<BatchFishing> list = fishingMapper.selectByBatchIds(batchIds);
        Map<Long, BatchFishing> map = new HashMap<>(list.size() * 2);
        for (BatchFishing item : list) {
            map.put(item.getBatchId(), item);
        }
        return map;
    }

    private Map<Long, BatchProcessing> indexProcessing(List<Long> batchIds) {
        List<BatchProcessing> list = processingMapper.selectByBatchIds(batchIds);
        Map<Long, BatchProcessing> map = new HashMap<>(list.size() * 2);
        for (BatchProcessing item : list) {
            map.put(item.getBatchId(), item);
        }
        return map;
    }

    private Map<Long, BatchWholesale> indexWholesale(List<Long> batchIds) {
        List<BatchWholesale> list = wholesaleMapper.selectByBatchIds(batchIds);
        Map<Long, BatchWholesale> map = new HashMap<>(list.size() * 2);
        for (BatchWholesale item : list) {
            map.put(item.getBatchId(), item);
        }
        return map;
    }

    private Map<Long, BatchRetail> indexRetail(List<Long> batchIds) {
        List<BatchRetail> list = retailMapper.selectByBatchIds(batchIds);
        Map<Long, BatchRetail> map = new HashMap<>(list.size() * 2);
        for (BatchRetail item : list) {
            map.put(item.getBatchId(), item);
        }
        return map;
    }

    private Map<Long, NodeEnterprise> indexEnterprise(List<ProductBatch> chain) {
        List<Long> enterpriseIds = new ArrayList<>(chain.size());
        for (ProductBatch batch : chain) {
            if (batch.getEnterpriseId() != null) {
                enterpriseIds.add(batch.getEnterpriseId());
            }
        }
        if (enterpriseIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 必须包含已逻辑删除的企业，否则历史批次的溯源页会缺失企业名称与所在地
        List<NodeEnterprise> enterprises = enterpriseMapper.selectByIdsIncludeDeleted(enterpriseIds);
        Map<Long, NodeEnterprise> map = new HashMap<>(enterprises.size() * 2);
        for (NodeEnterprise enterprise : enterprises) {
            map.put(enterprise.getId(), enterprise);
        }
        return map;
    }
}
