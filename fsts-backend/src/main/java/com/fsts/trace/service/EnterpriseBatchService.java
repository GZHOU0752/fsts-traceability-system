package com.fsts.trace.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.Constants;
import com.fsts.trace.common.ErrorCode;
import com.fsts.trace.common.LoginUser;
import com.fsts.trace.dto.query.BatchQuery;
import com.fsts.trace.dto.request.BatchSaveRequest;
import com.fsts.trace.dto.request.BatchUpdateRequest;
import com.fsts.trace.dto.request.OffShelfRequest;
import com.fsts.trace.entity.BatchConfirmRequest;
import com.fsts.trace.entity.BatchFishing;
import com.fsts.trace.entity.BatchProcessing;
import com.fsts.trace.entity.BatchRetail;
import com.fsts.trace.entity.BatchWholesale;
import com.fsts.trace.entity.ProductBatch;
import com.fsts.trace.mapper.BatchConfirmRequestMapper;
import com.fsts.trace.mapper.BatchFishingMapper;
import com.fsts.trace.mapper.BatchProcessingMapper;
import com.fsts.trace.mapper.BatchRetailMapper;
import com.fsts.trace.mapper.BatchWholesaleMapper;
import com.fsts.trace.mapper.ProductBatchMapper;
import com.fsts.trace.service.support.ColdChainService;
import com.fsts.trace.support.sequence.SequenceService;
import com.fsts.trace.service.validator.BatchValidatorRegistry;
import com.fsts.trace.vo.BatchDetailVO;
import com.fsts.trace.vo.BatchFishingInfoVO;
import com.fsts.trace.vo.BatchListItemVO;
import com.fsts.trace.vo.BatchProcessingInfoVO;
import com.fsts.trace.vo.BatchPublishResultVO;
import com.fsts.trace.vo.BatchRetailInfoVO;
import com.fsts.trace.vo.BatchSaveResultVO;
import com.fsts.trace.vo.BatchWholesaleInfoVO;
import com.fsts.trace.vo.CheckAvailableVO;
import com.fsts.trace.vo.ConfirmRequestBriefVO;
import com.fsts.trace.vo.PageVO;
import com.fsts.trace.vo.TraceCodeInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 流通节点端 - 产品批号管理（接口 10.1 ~ 10.8、10.10）。
 */
@Slf4j
@Service
public class EnterpriseBatchService {

    private final ProductBatchMapper batchMapper;
    private final BatchFishingMapper fishingMapper;
    private final BatchProcessingMapper processingMapper;
    private final BatchWholesaleMapper wholesaleMapper;
    private final BatchRetailMapper retailMapper;
    private final BatchConfirmRequestMapper confirmRequestMapper;
    private final BatchValidatorRegistry validatorRegistry;
    private final ColdChainService coldChainService;
    private final UpstreamService upstreamService;
    private final DictService dictService;
    private final TraceCodeService traceCodeService;
    private final SequenceService sequenceService;

    public EnterpriseBatchService(ProductBatchMapper batchMapper,
                                  BatchFishingMapper fishingMapper,
                                  BatchProcessingMapper processingMapper,
                                  BatchWholesaleMapper wholesaleMapper,
                                  BatchRetailMapper retailMapper,
                                  BatchConfirmRequestMapper confirmRequestMapper,
                                  BatchValidatorRegistry validatorRegistry,
                                  ColdChainService coldChainService,
                                  UpstreamService upstreamService,
                                  DictService dictService,
                                  TraceCodeService traceCodeService,
                                  SequenceService sequenceService) {
        this.batchMapper = batchMapper;
        this.fishingMapper = fishingMapper;
        this.processingMapper = processingMapper;
        this.wholesaleMapper = wholesaleMapper;
        this.retailMapper = retailMapper;
        this.confirmRequestMapper = confirmRequestMapper;
        this.validatorRegistry = validatorRegistry;
        this.coldChainService = coldChainService;
        this.upstreamService = upstreamService;
        this.dictService = dictService;
        this.traceCodeService = traceCodeService;
        this.sequenceService = sequenceService;
    }

    // ==================================================================
    // 10.1 分页查询
    // ==================================================================

    /**
     * 分页查询本企业产品批号（接口 10.1）。
     *
     * <p>数据隔离：enterpriseId 取自 Token，前端无法影响；
     * 已下架（4）的批号按需求直接过滤，不在列表中返回。
     */
    public PageVO<BatchListItemVO> page(LoginUser user, BatchQuery query) {
        Integer status = query.getBatchStatus();
        if (status == null || status == Constants.BATCH_STATUS_OFF_SHELF) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "已下架批号不可浏览，请选择新建 / 待确认 / 已确认分组");
        }

        Page<ProductBatch> page = new Page<>(query.safeCurrent(), query.safeSize());
        IPage<ProductBatch> result = batchMapper.selectPage(page, Wrappers.<ProductBatch>lambdaQuery()
                .eq(ProductBatch::getEnterpriseId, user.getEnterpriseId())
                .eq(ProductBatch::getBatchStatus, status)
                .eq(ProductBatch::getDeleted, 0)
                .like(StringUtils.hasText(query.getBatchNo()), ProductBatch::getBatchNo, query.getBatchNo())
                .eq(StringUtils.hasText(query.getProductVariety()), ProductBatch::getProductVariety, query.getProductVariety())
                .like(StringUtils.hasText(query.getUpstreamBatchNo()), ProductBatch::getUpstreamBatchNo, query.getUpstreamBatchNo())
                .orderByDesc(ProductBatch::getCreateTime)
                .orderByDesc(ProductBatch::getId));

        return PageVO.of(result, this::toListItem);
    }

    private BatchListItemVO toListItem(ProductBatch b) {
        return BatchListItemVO.builder()
                .id(b.getId())
                .batchNo(b.getBatchNo())
                .productVariety(b.getProductVariety())
                .upstreamBatchNo(b.getUpstreamBatchNo())
                .upstreamEnterpriseName(b.getUpstreamEnterpriseName())
                .batchStatus(b.getBatchStatus())
                .batchStatusName(dictService.batchStatusName(b.getBatchStatus()))
                .handoverTemp(b.getHandoverTemp())
                .coldChainOk(b.getColdChainOk() == null || b.getColdChainOk() == 1)
                .createTime(b.getCreateTime())
                .build();
    }

    // ==================================================================
    // 10.2 详情
    // ==================================================================

    /**
     * 查询产品批号详情（接口 10.2）。
     */
    public BatchDetailVO detail(LoginUser user, Long id) {
        ProductBatch batch = requireOwnedBatch(user, id);

        BatchDetailVO.BatchDetailVOBuilder builder = BatchDetailVO.builder()
                .id(batch.getId())
                .batchNo(batch.getBatchNo())
                .enterpriseId(batch.getEnterpriseId())
                .enterpriseName(batch.getEnterpriseName())
                .enterpriseType(batch.getEnterpriseType())
                .enterpriseTypeName(dictService.enterpriseTypeName(batch.getEnterpriseType()))
                .upstreamEnterpriseId(batch.getUpstreamEnterpriseId())
                .upstreamEnterpriseName(batch.getUpstreamEnterpriseName())
                .upstreamProvinceCode(batch.getUpstreamProvinceCode())
                .upstreamProvinceName(batch.getUpstreamProvinceName())
                .upstreamCityCode(batch.getUpstreamCityCode())
                .upstreamCityName(batch.getUpstreamCityName())
                .upstreamBatchId(batch.getUpstreamBatchId())
                .upstreamBatchNo(batch.getUpstreamBatchNo())
                .productVariety(batch.getProductVariety())
                .sourceType(batch.getSourceType())
                .sourceTypeName(dictService.sourceTypeName(batch.getSourceType()))
                .batchStatus(batch.getBatchStatus())
                .batchStatusName(dictService.batchStatusName(batch.getBatchStatus()))
                .handoverTemp(batch.getHandoverTemp())
                .coldChainOk(batch.getColdChainOk() == null || batch.getColdChainOk() == 1)
                .publishTime(batch.getPublishTime())
                .offShelfTime(batch.getOffShelfTime())
                .remark(batch.getRemark())
                .createTime(batch.getCreateTime())
                .updateTime(batch.getUpdateTime());

        // 明细对象按企业类型只装配其中一个
        Integer type = batch.getEnterpriseType();
        if (type != null) {
            switch (type) {
                case Constants.ENTERPRISE_TYPE_FISHING -> builder.fishingInfo(loadFishingInfo(batch.getId()));
                case Constants.ENTERPRISE_TYPE_PROCESSING -> builder.processingInfo(loadProcessingInfo(batch.getId()));
                case Constants.ENTERPRISE_TYPE_WHOLESALE -> builder.wholesaleInfo(loadWholesaleInfo(batch.getId()));
                case Constants.ENTERPRISE_TYPE_RETAIL -> builder.retailInfo(loadRetailInfo(batch.getId()));
                default -> {
                    // 未知类型不返回明细
                }
            }
        }

        builder.traceCode(loadTraceCodeInfo(batch.getId()));
        builder.confirmRequest(loadConfirmRequestBrief(batch.getId()));
        return builder.build();
    }

    // ==================================================================
    // 10.3 新建
    // ==================================================================

    /**
     * 新建产品批号（接口 10.3）。
     *
     * <p>事务边界：主表 + 明细表 + 确认请求必须原子提交，
     * 否则会出现"批号已建但明细缺失"的半成品数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchSaveResultVO create(LoginUser user, BatchSaveRequest request) {
        Integer type = user.getEnterpriseType();
        validatorRegistry.of(type).validate(request);

        ensureBatchNoUnique(user.getEnterpriseId(), request.getBatchNo(), null);

        ProductBatch batch = new ProductBatch();
        batch.setBatchNo(request.getBatchNo());
        batch.setEnterpriseId(user.getEnterpriseId());
        batch.setEnterpriseName(user.getEnterpriseName());
        batch.setEnterpriseType(type);
        batch.setRemark(request.getRemark());
        batch.setDeleted(0);
        batch.setBatchStatus(Constants.BATCH_STATUS_NEW);

        applyUpstreamIfNeeded(batch, request, type);

        ColdChainService.ColdChainResult coldChain = coldChainService.judge(type, request);
        coldChainService.assertAcceptable(coldChain, request.getForceSubmit());
        batch.setHandoverTemp(coldChain.handoverTemp());
        batch.setColdChainOk(coldChain.coldChainOk() ? 1 : 0);

        batchMapper.insert(batch);
        insertDetail(batch.getId(), type, request);

        String confirmRequestNo = null;
        if (type == Constants.ENTERPRISE_TYPE_FISHING && Boolean.TRUE.equals(request.getPublish())) {
            int affected = batchMapper.publishIfNew(batch.getId(), user.getEnterpriseId(), LocalDateTime.now());
            if (affected > 0) {
                batch.setBatchStatus(Constants.BATCH_STATUS_CONFIRMED);
                batch.setPublishTime(LocalDateTime.now());
            }
        } else if (type != Constants.ENTERPRISE_TYPE_FISHING && Boolean.TRUE.equals(request.getSendConfirmRequest())) {
            confirmRequestNo = submitConfirmRequest(user, batch, coldChain.handoverTemp());
            batch.setBatchStatus(Constants.BATCH_STATUS_PENDING);
        }

        log.info("新建产品批号成功: enterpriseId={} batchNo={} status={}",
                user.getEnterpriseId(), batch.getBatchNo(), batch.getBatchStatus());

        return BatchSaveResultVO.builder()
                .id(batch.getId())
                .batchNo(batch.getBatchNo())
                .batchStatus(batch.getBatchStatus())
                .batchStatusName(dictService.batchStatusName(batch.getBatchStatus()))
                .handoverTemp(batch.getHandoverTemp())
                .coldChainOk(batch.getColdChainOk() == 1)
                .confirmRequestNo(confirmRequestNo)
                .traceCode(batch.getBatchStatus() == Constants.BATCH_STATUS_CONFIRMED
                        ? loadTraceCodeInfo(batch.getId()) : null)
                .build();
    }

    // ==================================================================
    // 10.4 更新
    // ==================================================================

    /**
     * 更新产品批号（接口 10.4）。
     *
     * <p>只有"新建"状态允许更新；批号本身不可修改（传入即忽略）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchSaveResultVO update(LoginUser user, Long id, BatchUpdateRequest request) {
        ProductBatch batch = requireOwnedBatch(user, id);
        if (batch.getBatchStatus() != Constants.BATCH_STATUS_NEW) {
            throw BusinessException.of(ErrorCode.BATCH_STATUS_NOT_ALLOWED, "仅新建状态的批号允许更新");
        }
        // batchNo 不可修改：把路径上的既有批号回填，忽略请求体中的值
        request.setBatchNo(batch.getBatchNo());

        Integer type = user.getEnterpriseType();
        validatorRegistry.of(type).validate(request);

        applyUpstreamIfNeeded(batch, request, type);

        ColdChainService.ColdChainResult coldChain = coldChainService.judge(type, request);
        coldChainService.assertAcceptable(coldChain, request.getForceSubmit());
        batch.setHandoverTemp(coldChain.handoverTemp());
        batch.setColdChainOk(coldChain.coldChainOk() ? 1 : 0);
        batch.setRemark(request.getRemark());
        batchMapper.updateById(batch);

        updateDetail(batch.getId(), type, request);

        String confirmRequestNo = null;
        if (type == Constants.ENTERPRISE_TYPE_FISHING && Boolean.TRUE.equals(request.getPublish())) {
            int affected = batchMapper.publishIfNew(batch.getId(), user.getEnterpriseId(), LocalDateTime.now());
            if (affected > 0) {
                batch.setBatchStatus(Constants.BATCH_STATUS_CONFIRMED);
                batch.setPublishTime(LocalDateTime.now());
            }
        } else if (type != Constants.ENTERPRISE_TYPE_FISHING && Boolean.TRUE.equals(request.getSendConfirmRequest())) {
            confirmRequestNo = submitConfirmRequest(user, batch, coldChain.handoverTemp());
            batch.setBatchStatus(Constants.BATCH_STATUS_PENDING);
        }

        return BatchSaveResultVO.builder()
                .id(batch.getId())
                .batchNo(batch.getBatchNo())
                .batchStatus(batch.getBatchStatus())
                .batchStatusName(dictService.batchStatusName(batch.getBatchStatus()))
                .handoverTemp(batch.getHandoverTemp())
                .coldChainOk(batch.getColdChainOk() == 1)
                .confirmRequestNo(confirmRequestNo)
                .traceCode(batch.getBatchStatus() == Constants.BATCH_STATUS_CONFIRMED
                        ? loadTraceCodeInfo(batch.getId()) : null)
                .build();
    }

    // ==================================================================
    // 10.5 删除
    // ==================================================================

    /**
     * 删除产品批号（接口 10.5）：物理删除，仅允许"新建"状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(LoginUser user, Long id) {
        ProductBatch batch = requireOwnedBatch(user, id);
        if (batch.getBatchStatus() != Constants.BATCH_STATUS_NEW) {
            throw BusinessException.of(ErrorCode.BATCH_STATUS_NOT_ALLOWED, "仅新建状态的批号允许删除");
        }
        // CAS 删除：即便并发下状态刚被改变，也不会误删
        int affected = batchMapper.deleteIfNew(id, user.getEnterpriseId());
        if (affected == 0) {
            throw BusinessException.of(ErrorCode.BATCH_STATUS_NOT_ALLOWED, "批号状态已变化，请刷新后重试");
        }
        log.info("产品批号已删除: enterpriseId={} batchId={} batchNo={}",
                user.getEnterpriseId(), id, batch.getBatchNo());
    }

    // ==================================================================
    // 10.6 发布（仅捕捞与养殖企业）
    // ==================================================================

    /**
     * 发布产品批号（接口 10.6）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchPublishResultVO publish(LoginUser user, Long id) {
        if (user.getEnterpriseType() == null
                || user.getEnterpriseType() != Constants.ENTERPRISE_TYPE_FISHING) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "请使用向上游企业发送确认请求接口");
        }
        ProductBatch batch = requireOwnedBatch(user, id);
        if (batch.getBatchStatus() != Constants.BATCH_STATUS_NEW) {
            throw BusinessException.of(ErrorCode.BATCH_STATUS_NOT_ALLOWED, "仅新建状态的批号允许发布");
        }
        LocalDateTime now = LocalDateTime.now();
        int affected = batchMapper.publishIfNew(id, user.getEnterpriseId(), now);
        if (affected == 0) {
            throw BusinessException.of(ErrorCode.BATCH_STATUS_NOT_ALLOWED, "批号状态已变化，请刷新后重试");
        }
        return BatchPublishResultVO.builder()
                .id(batch.getId())
                .batchNo(batch.getBatchNo())
                .batchStatus(Constants.BATCH_STATUS_CONFIRMED)
                .batchStatusName(dictService.batchStatusName(Constants.BATCH_STATUS_CONFIRMED))
                .publishTime(now)
                .build();
    }

    // ==================================================================
    // 10.7 下架
    // ==================================================================

    /**
     * 下架产品批号（接口 10.7）。
     *
     * <p>下架后：批号不可被下游选择；零售商批号的溯源标识码同步失效。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchPublishResultVO offShelf(LoginUser user, Long id, OffShelfRequest request) {
        ProductBatch batch = requireOwnedBatch(user, id);
        if (batch.getBatchStatus() != Constants.BATCH_STATUS_CONFIRMED) {
            throw BusinessException.of(ErrorCode.BATCH_STATUS_NOT_ALLOWED, "仅已确认/已发布的批号允许下架");
        }
        LocalDateTime now = LocalDateTime.now();
        String remark = request == null ? null : request.getRemark();
        int affected = batchMapper.offShelfIfConfirmed(id, user.getEnterpriseId(), now, remark);
        if (affected == 0) {
            throw BusinessException.of(ErrorCode.BATCH_STATUS_NOT_ALLOWED, "批号状态已变化，请刷新后重试");
        }

        // 零售商的溯源标识码同步失效，消费者查询将返回 3002
        if (batch.getEnterpriseType() != null
                && batch.getEnterpriseType() == Constants.ENTERPRISE_TYPE_RETAIL) {
            traceCodeService.invalidateByBatchId(id);
        }

        return BatchPublishResultVO.builder()
                .id(batch.getId())
                .batchNo(batch.getBatchNo())
                .batchStatus(Constants.BATCH_STATUS_OFF_SHELF)
                .batchStatusName(dictService.batchStatusName(Constants.BATCH_STATUS_OFF_SHELF))
                .offShelfTime(now)
                .build();
    }

    // ==================================================================
    // 10.8 唯一性校验
    // ==================================================================

    /**
     * 产品批号唯一性校验（接口 10.8）：校验范围为当前企业。
     */
    public CheckAvailableVO checkBatchNo(LoginUser user, String batchNo, Long excludeId) {
        if (!StringUtils.hasText(batchNo)) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "产品批号不能为空");
        }
        Long count = batchMapper.selectCount(Wrappers.<ProductBatch>lambdaQuery()
                .eq(ProductBatch::getEnterpriseId, user.getEnterpriseId())
                .eq(ProductBatch::getBatchNo, batchNo)
                .eq(ProductBatch::getDeleted, 0)
                .ne(excludeId != null, ProductBatch::getId, excludeId));
        return new CheckAvailableVO(count == null || count == 0);
    }

    // ==================================================================
    // 10.10 查询溯源标识码
    // ==================================================================

    /**
     * 查询溯源标识码（接口 10.10）。
     */
    public TraceCodeInfoVO queryTraceCode(LoginUser user, Long id) {
        if (user.getEnterpriseType() == null
                || user.getEnterpriseType() != Constants.ENTERPRISE_TYPE_RETAIL) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "仅零售商可查询溯源标识码");
        }
        ProductBatch batch = requireOwnedBatch(user, id);
        if (batch.getBatchStatus() == null || batch.getBatchStatus() != Constants.BATCH_STATUS_CONFIRMED) {
            throw BusinessException.of(ErrorCode.CONFLICT, "该批号尚未确认，暂无溯源标识码");
        }
        TraceCodeInfoVO info = loadTraceCodeInfo(id);
        if (info == null) {
            throw BusinessException.of(ErrorCode.CONFLICT, "该批号尚未确认，暂无溯源标识码");
        }
        return info;
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    /**
     * 取本企业批号，非本企业直接 403（接口 10.2 业务规则 1）。
     */
    ProductBatch requireOwnedBatch(LoginUser user, Long id) {
        ProductBatch batch = batchMapper.selectOne(Wrappers.<ProductBatch>lambdaQuery()
                .eq(ProductBatch::getId, id)
                .eq(ProductBatch::getDeleted, 0)
                .last("LIMIT 1"));
        if (batch == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND, "产品批号不存在");
        }
        if (!batch.getEnterpriseId().equals(user.getEnterpriseId())) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "不能操作其他企业的产品批号");
        }
        return batch;
    }

    private void ensureBatchNoUnique(Long enterpriseId, String batchNo, Long excludeId) {
        Long count = batchMapper.selectCount(Wrappers.<ProductBatch>lambdaQuery()
                .eq(ProductBatch::getEnterpriseId, enterpriseId)
                .eq(ProductBatch::getBatchNo, batchNo)
                .eq(ProductBatch::getDeleted, 0)
                .ne(excludeId != null, ProductBatch::getId, excludeId));
        if (count != null && count > 0) {
            throw BusinessException.of(ErrorCode.BATCH_NO_EXISTS);
        }
    }

    /**
     * 上游带出：类型 2/3/4 从上游批号继承品种、来源类型与上游企业信息。
     */
    private void applyUpstreamIfNeeded(ProductBatch batch, BatchSaveRequest request, Integer type) {
        if (type == null || type == Constants.ENTERPRISE_TYPE_FISHING) {
            return;
        }
        UpstreamService.UpstreamSelection selection = upstreamService.requireAvailableUpstreamBatch(
                request.getUpstreamEnterpriseId(), request.getUpstreamBatchId());
        ProductBatch upstream = selection.batch();
        com.fsts.trace.entity.NodeEnterprise upstreamEnterprise = selection.enterprise();

        batch.setUpstreamEnterpriseId(upstreamEnterprise.getId());
        batch.setUpstreamEnterpriseName(upstreamEnterprise.getEnterpriseName());
        batch.setUpstreamProvinceCode(upstreamEnterprise.getProvinceCode());
        batch.setUpstreamProvinceName(upstreamEnterprise.getProvinceName());
        batch.setUpstreamCityCode(upstreamEnterprise.getCityCode());
        batch.setUpstreamCityName(upstreamEnterprise.getCityName());
        batch.setUpstreamBatchId(upstream.getId());
        batch.setUpstreamBatchNo(upstream.getBatchNo());
        // 上游带出：品种与来源类型不可由前端修改
        batch.setProductVariety(upstream.getProductVariety());
        batch.setSourceType(upstream.getSourceType());
    }

    private void insertDetail(Long batchId, Integer type, BatchSaveRequest request) {
        switch (type) {
            case Constants.ENTERPRISE_TYPE_FISHING -> {
                BatchFishing entity = new BatchFishing();
                entity.setBatchId(batchId);
                entity.setCatchBreedDate(request.getCatchBreedDate());
                entity.setCertificateType(request.getCertificateType());
                entity.setCertificateNo(request.getCertificateNo());
                entity.setDepartureTemp(request.getDepartureTemp());
                entity.setDrugReportNo(request.getDrugReportNo());
                entity.setFishingLogNo(request.getFishingLogNo());
                fishingMapper.insert(entity);
            }
            case Constants.ENTERPRISE_TYPE_PROCESSING -> {
                BatchProcessing entity = new BatchProcessing();
                entity.setBatchId(batchId);
                entity.setProcessForm(request.getProcessForm());
                entity.setInspectionNo(request.getInspectionNo());
                entity.setQuickFreezeTemp(request.getQuickFreezeTemp());
                entity.setFactoryTemp(request.getFactoryTemp());
                entity.setProductionBatchNo(request.getProductionBatchNo());
                processingMapper.insert(entity);
            }
            case Constants.ENTERPRISE_TYPE_WHOLESALE -> {
                BatchWholesale entity = new BatchWholesale();
                entity.setBatchId(batchId);
                entity.setWholesaleDate(request.getWholesaleDate());
                entity.setInboundTemp(request.getInboundTemp());
                entity.setColdStorageTemp(request.getColdStorageTemp());
                entity.setOutboundTemp(request.getOutboundTemp());
                entity.setTransportToolNo(request.getTransportToolNo());
                entity.setColdStorageNo(request.getColdStorageNo());
                wholesaleMapper.insert(entity);
            }
            case Constants.ENTERPRISE_TYPE_RETAIL -> {
                BatchRetail entity = new BatchRetail();
                entity.setBatchId(batchId);
                entity.setShelfDate(request.getShelfDate());
                entity.setDisplayTemp(request.getDisplayTemp());
                entity.setSaleStore(request.getSaleStore());
                entity.setDisplayEquipNo(request.getDisplayEquipNo());
                retailMapper.insert(entity);
            }
            default -> throw BusinessException.of(ErrorCode.PARAM_INVALID, "不支持的企业类型：" + type);
        }
    }

    private void updateDetail(Long batchId, Integer type, BatchSaveRequest request) {
        switch (type) {
            case Constants.ENTERPRISE_TYPE_FISHING -> {
                BatchFishing entity = fishingMapper.selectOne(Wrappers.<BatchFishing>lambdaQuery()
                        .eq(BatchFishing::getBatchId, batchId).last("LIMIT 1"));
                if (entity == null) {
                    insertDetail(batchId, type, request);
                    return;
                }
                entity.setCatchBreedDate(request.getCatchBreedDate());
                entity.setCertificateType(request.getCertificateType());
                entity.setCertificateNo(request.getCertificateNo());
                entity.setDepartureTemp(request.getDepartureTemp());
                entity.setDrugReportNo(request.getDrugReportNo());
                entity.setFishingLogNo(request.getFishingLogNo());
                fishingMapper.updateById(entity);
            }
            case Constants.ENTERPRISE_TYPE_PROCESSING -> {
                BatchProcessing entity = processingMapper.selectOne(Wrappers.<BatchProcessing>lambdaQuery()
                        .eq(BatchProcessing::getBatchId, batchId).last("LIMIT 1"));
                if (entity == null) {
                    insertDetail(batchId, type, request);
                    return;
                }
                entity.setProcessForm(request.getProcessForm());
                entity.setInspectionNo(request.getInspectionNo());
                entity.setQuickFreezeTemp(request.getQuickFreezeTemp());
                entity.setFactoryTemp(request.getFactoryTemp());
                entity.setProductionBatchNo(request.getProductionBatchNo());
                processingMapper.updateById(entity);
            }
            case Constants.ENTERPRISE_TYPE_WHOLESALE -> {
                BatchWholesale entity = wholesaleMapper.selectOne(Wrappers.<BatchWholesale>lambdaQuery()
                        .eq(BatchWholesale::getBatchId, batchId).last("LIMIT 1"));
                if (entity == null) {
                    insertDetail(batchId, type, request);
                    return;
                }
                entity.setWholesaleDate(request.getWholesaleDate());
                entity.setInboundTemp(request.getInboundTemp());
                entity.setColdStorageTemp(request.getColdStorageTemp());
                entity.setOutboundTemp(request.getOutboundTemp());
                entity.setTransportToolNo(request.getTransportToolNo());
                entity.setColdStorageNo(request.getColdStorageNo());
                wholesaleMapper.updateById(entity);
            }
            case Constants.ENTERPRISE_TYPE_RETAIL -> {
                BatchRetail entity = retailMapper.selectOne(Wrappers.<BatchRetail>lambdaQuery()
                        .eq(BatchRetail::getBatchId, batchId).last("LIMIT 1"));
                if (entity == null) {
                    insertDetail(batchId, type, request);
                    return;
                }
                entity.setShelfDate(request.getShelfDate());
                entity.setDisplayTemp(request.getDisplayTemp());
                entity.setSaleStore(request.getSaleStore());
                entity.setDisplayEquipNo(request.getDisplayEquipNo());
                retailMapper.updateById(entity);
            }
            default -> throw BusinessException.of(ErrorCode.PARAM_INVALID, "不支持的企业类型：" + type);
        }
    }

    BatchFishingInfoVO loadFishingInfo(Long batchId) {
        BatchFishing entity = fishingMapper.selectOne(Wrappers.<BatchFishing>lambdaQuery()
                .eq(BatchFishing::getBatchId, batchId).last("LIMIT 1"));
        if (entity == null) {
            return null;
        }
        return BatchFishingInfoVO.builder()
                .catchBreedDate(entity.getCatchBreedDate())
                .certificateType(entity.getCertificateType())
                .certificateTypeName(dictService.certificateTypeName(entity.getCertificateType()))
                .certificateNo(entity.getCertificateNo())
                .departureTemp(entity.getDepartureTemp())
                .drugReportNo(entity.getDrugReportNo())
                .fishingLogNo(entity.getFishingLogNo())
                .build();
    }

    BatchProcessingInfoVO loadProcessingInfo(Long batchId) {
        BatchProcessing entity = processingMapper.selectOne(Wrappers.<BatchProcessing>lambdaQuery()
                .eq(BatchProcessing::getBatchId, batchId).last("LIMIT 1"));
        if (entity == null) {
            return null;
        }
        return BatchProcessingInfoVO.builder()
                .processForm(entity.getProcessForm())
                .inspectionNo(entity.getInspectionNo())
                .quickFreezeTemp(entity.getQuickFreezeTemp())
                .factoryTemp(entity.getFactoryTemp())
                .productionBatchNo(entity.getProductionBatchNo())
                .build();
    }

    BatchWholesaleInfoVO loadWholesaleInfo(Long batchId) {
        BatchWholesale entity = wholesaleMapper.selectOne(Wrappers.<BatchWholesale>lambdaQuery()
                .eq(BatchWholesale::getBatchId, batchId).last("LIMIT 1"));
        if (entity == null) {
            return null;
        }
        return BatchWholesaleInfoVO.builder()
                .wholesaleDate(entity.getWholesaleDate())
                .inboundTemp(entity.getInboundTemp())
                .coldStorageTemp(entity.getColdStorageTemp())
                .outboundTemp(entity.getOutboundTemp())
                .transportToolNo(entity.getTransportToolNo())
                .coldStorageNo(entity.getColdStorageNo())
                .build();
    }

    BatchRetailInfoVO loadRetailInfo(Long batchId) {
        BatchRetail entity = loadRetailEntity(batchId);
        if (entity == null) {
            return null;
        }
        return BatchRetailInfoVO.builder()
                .shelfDate(entity.getShelfDate())
                .displayTemp(entity.getDisplayTemp())
                .saleStore(entity.getSaleStore())
                .displayEquipNo(entity.getDisplayEquipNo())
                .build();
    }

    BatchRetail loadRetailEntity(Long batchId) {
        return retailMapper.selectOne(Wrappers.<BatchRetail>lambdaQuery()
                .eq(BatchRetail::getBatchId, batchId).last("LIMIT 1"));
    }

    TraceCodeInfoVO loadTraceCodeInfo(Long batchId) {
        com.fsts.trace.entity.TraceCode traceCode = traceCodeService.getByBatchId(batchId);
        if (traceCode == null) {
            return null;
        }
        return TraceCodeInfoVO.builder()
                .traceCode(traceCode.getTraceCode())
                .batchId(traceCode.getBatchId())
                .batchNo(traceCode.getBatchNo())
                .productVariety(traceCode.getProductVariety())
                .saleStore(traceCode.getSaleStore())
                .qrContent(traceCode.getQrContent())
                .status(traceCode.getStatus())
                .statusName(traceCode.getStatus() != null && traceCode.getStatus() == 1 ? "有效" : "失效")
                .generateTime(traceCode.getGenerateTime())
                .queryCount(traceCode.getQueryCount())
                .lastQueryTime(traceCode.getLastQueryTime())
                .build();
    }

    ConfirmRequestBriefVO loadConfirmRequestBrief(Long batchId) {
        BatchConfirmRequest request = confirmRequestMapper.selectOne(Wrappers.<BatchConfirmRequest>lambdaQuery()
                .eq(BatchConfirmRequest::getBatchId, batchId).last("LIMIT 1"));
        if (request == null) {
            return null;
        }
        return ConfirmRequestBriefVO.builder()
                .requestNo(request.getRequestNo())
                .requestStatus(request.getRequestStatus())
                .requestStatusName(dictService.requestStatusName(request.getRequestStatus()))
                .requestTime(request.getRequestTime())
                .handleTime(request.getHandleTime())
                .handleRemark(request.getHandleRemark())
                .build();
    }

    /**
     * 生成或复用确认请求，并把批号置为待确认。
     *
     * <p>复用规则（接口 10.9 业务规则 4）：原请求被拒绝（状态 3）时复用原记录重新发起；
     * 已存在待确认请求时返回 409。
     */
    String submitConfirmRequest(LoginUser user, ProductBatch batch, java.math.BigDecimal handoverTemp) {
        if (batch.getUpstreamEnterpriseId() == null || batch.getUpstreamBatchId() == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "请先填写上游企业与上游产品批号");
        }
        BatchConfirmRequest existing = confirmRequestMapper.selectOne(Wrappers.<BatchConfirmRequest>lambdaQuery()
                .eq(BatchConfirmRequest::getBatchId, batch.getId()).last("LIMIT 1"));

        if (existing != null) {
            if (existing.getRequestStatus() != null
                    && existing.getRequestStatus() == Constants.REQUEST_STATUS_PENDING) {
                throw BusinessException.of(ErrorCode.CONFLICT, "该批号已存在待确认的进场确认请求");
            }
            if (existing.getRequestStatus() != null
                    && existing.getRequestStatus() == Constants.REQUEST_STATUS_REJECTED) {
                int affected = confirmRequestMapper.reopenRejected(existing.getId(), LocalDateTime.now(),
                        batch.getUpstreamBatchId(), batch.getUpstreamBatchNo(), handoverTemp);
                if (affected == 0) {
                    throw BusinessException.of(ErrorCode.CONFLICT, "确认请求状态已变化，请刷新后重试");
                }
                markPending(user, batch);
                return existing.getRequestNo();
            }
            // 已确认（2）的请求不允许重发
            throw BusinessException.of(ErrorCode.CONFLICT, "该批号已完成进场确认，无法重复发起");
        }

        LocalDateTime now = LocalDateTime.now();
        BatchConfirmRequest entity = new BatchConfirmRequest();
        entity.setRequestNo(generateRequestNo(now));
        entity.setBatchId(batch.getId());
        entity.setBatchNo(batch.getBatchNo());
        entity.setUpstreamBatchId(batch.getUpstreamBatchId());
        entity.setUpstreamBatchNo(batch.getUpstreamBatchNo());
        entity.setFromEnterpriseId(batch.getEnterpriseId());
        entity.setFromEnterpriseName(batch.getEnterpriseName());
        entity.setToEnterpriseId(batch.getUpstreamEnterpriseId());
        entity.setToEnterpriseName(batch.getUpstreamEnterpriseName());
        entity.setHandoverTemp(handoverTemp);
        entity.setRequestStatus(Constants.REQUEST_STATUS_PENDING);
        entity.setRequestTime(now);
        confirmRequestMapper.insert(entity);

        markPending(user, batch);
        return entity.getRequestNo();
    }

    private void markPending(LoginUser user, ProductBatch batch) {
        int affected = batchMapper.markPendingIfNew(batch.getId(), user.getEnterpriseId());
        if (affected == 0) {
            throw BusinessException.of(ErrorCode.BATCH_STATUS_NOT_ALLOWED, "批号状态已变化，无法发起确认请求");
        }
    }

    /**
     * 确认请求单号：FSTS-CR-yyyyMMdd-{4 位流水}。
     */
    String generateRequestNo(LocalDateTime time) {
        String datePart = time.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = sequenceService.next("request:" + datePart);
        return Constants.REQUEST_NO_PREFIX + datePart + "-" + String.format("%04d", seq);
    }
}
