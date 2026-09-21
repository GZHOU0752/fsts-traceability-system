package com.fsts.trace.service;

import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.Constants;
import com.fsts.trace.common.ErrorCode;
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
import com.fsts.trace.support.counter.TraceQueryCounter;
import com.fsts.trace.vo.CertificateVO;
import com.fsts.trace.vo.PublicTraceVO;
import com.fsts.trace.vo.TemperatureItemVO;
import com.fsts.trace.vo.TemperaturePointVO;
import com.fsts.trace.vo.TraceLinkVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费者端 - 溯源链组装（接口 12.1）。
 *
 * <p><b>性能设计：</b>
 * 1. 查询次数固定为 6 次 SQL（1 次标识码 + 1 次递归链 + 4 次明细），
 *    与链条长度无关，不存在 N+1；
 * 2. 明细表按 batch_id 批量 IN 查询，命中唯一索引；
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

    private final TraceCodeService traceCodeService;
    private final ProductBatchMapper batchMapper;
    private final BatchFishingMapper fishingMapper;
    private final BatchProcessingMapper processingMapper;
    private final BatchWholesaleMapper wholesaleMapper;
    private final BatchRetailMapper retailMapper;
    private final NodeEnterpriseMapper enterpriseMapper;
    private final DictService dictService;
    private final TraceQueryCounter queryCounter;

    public TraceChainService(TraceCodeService traceCodeService,
                             ProductBatchMapper batchMapper,
                             BatchFishingMapper fishingMapper,
                             BatchProcessingMapper processingMapper,
                             BatchWholesaleMapper wholesaleMapper,
                             BatchRetailMapper retailMapper,
                             NodeEnterpriseMapper enterpriseMapper,
                             DictService dictService,
                             TraceQueryCounter queryCounter) {
        this.traceCodeService = traceCodeService;
        this.batchMapper = batchMapper;
        this.fishingMapper = fishingMapper;
        this.processingMapper = processingMapper;
        this.wholesaleMapper = wholesaleMapper;
        this.retailMapper = retailMapper;
        this.enterpriseMapper = enterpriseMapper;
        this.dictService = dictService;
        this.queryCounter = queryCounter;
    }

    /**
     * 按溯源标识码查询溯源信息（接口 12.1）。
     */
    public PublicTraceVO query(String traceCodeValue) {
        TraceCode traceCode = traceCodeService.getByTraceCode(traceCodeValue);
        if (traceCode == null) {
            throw BusinessException.of(ErrorCode.TRACE_CODE_NOT_FOUND);
        }
        if (traceCode.getStatus() == null || traceCode.getStatus() != 1) {
            throw BusinessException.of(ErrorCode.TRACE_CODE_INVALID);
        }

        List<ProductBatch> chain = batchMapper.selectTraceChain(traceCode.getBatchId());
        if (chain == null || chain.isEmpty()) {
            throw BusinessException.of(ErrorCode.TRACE_CODE_NOT_FOUND);
        }
        // 递归结果按环节升序返回：1 捕捞与养殖 -> 2 冷冻加工 -> 3 批发 -> 4 零售
        chain.sort((a, b) -> Integer.compare(
                a.getEnterpriseType() == null ? 0 : a.getEnterpriseType(),
                b.getEnterpriseType() == null ? 0 : b.getEnterpriseType()));

        List<Long> batchIds = new ArrayList<>(chain.size());
        for (ProductBatch batch : chain) {
            batchIds.add(batch.getId());
        }
        Map<Long, BatchFishing> fishingMap = indexFishing(batchIds);
        Map<Long, BatchProcessing> processingMap = indexProcessing(batchIds);
        Map<Long, BatchWholesale> wholesaleMap = indexWholesale(batchIds);
        Map<Long, BatchRetail> retailMap = indexRetail(batchIds);
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
                                    Constants.TEMP_THRESHOLD_NORMAL,
                                    ColdChainService.isQualified(detail.getDepartureTemp(), Constants.TEMP_THRESHOLD_NORMAL)));
                        }
                    }
                    case Constants.ENTERPRISE_TYPE_PROCESSING -> {
                        BatchProcessing detail = processingMap.get(batch.getId());
                        if (detail != null) {
                            certificates.add(new CertificateVO("出厂检验报告", detail.getInspectionNo()));
                            temperatures.add(new TemperatureItemVO("速冻中心温度", detail.getQuickFreezeTemp(),
                                    Constants.TEMP_THRESHOLD_QUICK_FREEZE,
                                    ColdChainService.isQualified(detail.getQuickFreezeTemp(), Constants.TEMP_THRESHOLD_QUICK_FREEZE)));
                            temperatures.add(new TemperatureItemVO("出厂温度", detail.getFactoryTemp(),
                                    Constants.TEMP_THRESHOLD_NORMAL,
                                    ColdChainService.isQualified(detail.getFactoryTemp(), Constants.TEMP_THRESHOLD_NORMAL)));
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
                                    Constants.TEMP_THRESHOLD_NORMAL,
                                    ColdChainService.isQualified(detail.getInboundTemp(), Constants.TEMP_THRESHOLD_NORMAL)));
                            temperatures.add(new TemperatureItemVO("冷库温度", detail.getColdStorageTemp(),
                                    Constants.TEMP_THRESHOLD_NORMAL,
                                    ColdChainService.isQualified(detail.getColdStorageTemp(), Constants.TEMP_THRESHOLD_NORMAL)));
                            temperatures.add(new TemperatureItemVO("出库温度", detail.getOutboundTemp(),
                                    Constants.TEMP_THRESHOLD_NORMAL,
                                    ColdChainService.isQualified(detail.getOutboundTemp(), Constants.TEMP_THRESHOLD_NORMAL)));
                            curveTemp = detail.getOutboundTemp();
                        }
                    }
                    case Constants.ENTERPRISE_TYPE_RETAIL -> {
                        BatchRetail detail = retailMap.get(batch.getId());
                        if (detail != null) {
                            temperatures.add(new TemperatureItemVO("冷冻陈列柜温度", detail.getDisplayTemp(),
                                    Constants.TEMP_THRESHOLD_NORMAL,
                                    ColdChainService.isQualified(detail.getDisplayTemp(), Constants.TEMP_THRESHOLD_NORMAL)));
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
                    .threshold(Constants.TEMP_THRESHOLD_NORMAL)
                    .qualified(stageOk && ColdChainService.isQualified(curveTemp, Constants.TEMP_THRESHOLD_NORMAL))
                    .recordTime(batch.getPublishTime())
                    .build());
        }

        boolean coldChainQualified = abnormalStages.isEmpty();
        NodeEnterprise retailer = enterpriseMap.get(traceCode.getRetailerId());

        // 读路径只做内存自增，真正的 UPDATE 由定时任务批量完成
        queryCounter.record(traceCode.getId());

        return PublicTraceVO.builder()
                .traceCode(traceCode.getTraceCode())
                .batchNo(traceCode.getBatchNo())
                .productVariety(traceCode.getProductVariety())
                .retailerName(retailer == null ? traceCode.getRetailerName() : retailer.getEnterpriseName())
                .saleStore(traceCode.getSaleStore())
                .generateTime(traceCode.getGenerateTime())
                .queryCount((int) queryCounter.currentCount(traceCode.getId(), traceCode.getQueryCount()))
                .coldChainQualified(coldChainQualified)
                .coldChainConclusion(buildConclusion(chain.size(), coldChainQualified, abnormalStages))
                .temperatureCurve(curve)
                .links(links)
                .build();
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
