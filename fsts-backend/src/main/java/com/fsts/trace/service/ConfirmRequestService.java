package com.fsts.trace.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.Constants;
import com.fsts.trace.common.ErrorCode;
import com.fsts.trace.common.LoginUser;
import com.fsts.trace.dto.query.ConfirmRequestQuery;
import com.fsts.trace.entity.BatchConfirmRequest;
import com.fsts.trace.entity.BatchProcessing;
import com.fsts.trace.entity.BatchRetail;
import com.fsts.trace.entity.BatchWholesale;
import com.fsts.trace.entity.NodeEnterprise;
import com.fsts.trace.entity.ProductBatch;
import com.fsts.trace.entity.TraceCode;
import com.fsts.trace.mapper.BatchConfirmRequestMapper;
import com.fsts.trace.mapper.BatchProcessingMapper;
import com.fsts.trace.mapper.BatchWholesaleMapper;
import com.fsts.trace.mapper.NodeEnterpriseMapper;
import com.fsts.trace.mapper.ProductBatchMapper;
import com.fsts.trace.vo.ConfirmRequestDetailVO;
import com.fsts.trace.vo.ConfirmRequestListItemVO;
import com.fsts.trace.vo.ConfirmRequestSendVO;
import com.fsts.trace.vo.ConfirmResultVO;
import com.fsts.trace.vo.DownstreamBatchVO;
import com.fsts.trace.vo.PageVO;
import com.fsts.trace.vo.RejectResultVO;
import com.fsts.trace.vo.TraceCodeInfoVO;
import com.fsts.trace.vo.UpstreamBatchSimpleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 流通节点端 - 下游企业进场确认（接口 10.9、11.1 ~ 11.4）。
 */
@Slf4j
@Service
public class ConfirmRequestService {

    private final BatchConfirmRequestMapper confirmRequestMapper;
    private final ProductBatchMapper batchMapper;
    private final BatchProcessingMapper processingMapper;
    private final BatchWholesaleMapper wholesaleMapper;
    private final NodeEnterpriseMapper enterpriseMapper;
    private final DictService dictService;
    private final TraceCodeService traceCodeService;
    private final EnterpriseBatchService batchService;

    public ConfirmRequestService(BatchConfirmRequestMapper confirmRequestMapper,
                                 ProductBatchMapper batchMapper,
                                 BatchProcessingMapper processingMapper,
                                 BatchWholesaleMapper wholesaleMapper,
                                 NodeEnterpriseMapper enterpriseMapper,
                                 DictService dictService,
                                 TraceCodeService traceCodeService,
                                 EnterpriseBatchService batchService) {
        this.confirmRequestMapper = confirmRequestMapper;
        this.batchMapper = batchMapper;
        this.processingMapper = processingMapper;
        this.wholesaleMapper = wholesaleMapper;
        this.enterpriseMapper = enterpriseMapper;
        this.dictService = dictService;
        this.traceCodeService = traceCodeService;
        this.batchService = batchService;
    }

    // ==================================================================
    // 11.1 待处理列表
    // ==================================================================

    /**
     * 待处理进场确认请求列表（接口 11.1）。
     *
     * <p>只返回"接收方为本企业"的请求，天然实现企业间数据隔离；
     * 零售商（类型 4）处于链条末端，不存在下游确认场景。
     */
    public PageVO<ConfirmRequestListItemVO> page(LoginUser user, ConfirmRequestQuery query) {
        requireConfirmableType(user.getEnterpriseType());
        Page<ConfirmRequestListItemVO> page = new Page<>(query.safeCurrent(), query.safeSize());
        IPage<ConfirmRequestListItemVO> result =
                confirmRequestMapper.selectRequestPage(page, query, user.getEnterpriseId());
        return PageVO.of(result);
    }

    // ==================================================================
    // 11.2 详情
    // ==================================================================

    /**
     * 确认请求详情（接口 11.2）：请求的接收方或发起方均可查看。
     */
    public ConfirmRequestDetailVO detail(LoginUser user, Long id) {
        BatchConfirmRequest request = requireVisibleRequest(user, id);
        ProductBatch downstream = batchMapper.selectById(request.getBatchId());
        ProductBatch upstream = batchMapper.selectById(request.getUpstreamBatchId());
        NodeEnterprise fromEnterprise = enterpriseMapper.selectById(request.getFromEnterpriseId());

        return ConfirmRequestDetailVO.builder()
                .id(request.getId())
                .requestNo(request.getRequestNo())
                .requestStatus(request.getRequestStatus())
                .requestStatusName(dictService.requestStatusName(request.getRequestStatus()))
                .requestTime(request.getRequestTime())
                .handleTime(request.getHandleTime())
                .handleRemark(request.getHandleRemark())
                .fromEnterpriseName(request.getFromEnterpriseName())
                .fromEnterpriseTypeName(downstream == null ? null
                        : dictService.enterpriseTypeName(downstream.getEnterpriseType()))
                .fromProvinceName(fromEnterprise == null ? null : fromEnterprise.getProvinceName())
                .fromCityName(fromEnterprise == null ? null : fromEnterprise.getCityName())
                .toEnterpriseName(request.getToEnterpriseName())
                .downstreamBatch(buildDownstreamBatch(downstream, request))
                .upstreamBatch(buildUpstreamBatch(upstream, request))
                .build();
    }

    private DownstreamBatchVO buildDownstreamBatch(ProductBatch downstream, BatchConfirmRequest request) {
        if (downstream == null) {
            return null;
        }
        DownstreamBatchVO.DownstreamBatchVOBuilder builder = DownstreamBatchVO.builder()
                .batchId(downstream.getId())
                .batchNo(downstream.getBatchNo())
                .productVariety(downstream.getProductVariety())
                .handoverTemp(request.getHandoverTemp() != null
                        ? request.getHandoverTemp() : downstream.getHandoverTemp())
                .coldChainOk(downstream.getColdChainOk() == null || downstream.getColdChainOk() == 1);

        Integer type = downstream.getEnterpriseType();
        if (type != null) {
            if (type == Constants.ENTERPRISE_TYPE_PROCESSING) {
                BatchProcessing processing = processingMapper.selectOne(Wrappers.<BatchProcessing>lambdaQuery()
                        .eq(BatchProcessing::getBatchId, downstream.getId()).last("LIMIT 1"));
                if (processing != null) {
                    builder.quickFreezeTemp(processing.getQuickFreezeTemp())
                            .factoryTemp(processing.getFactoryTemp());
                }
            } else if (type == Constants.ENTERPRISE_TYPE_WHOLESALE) {
                BatchWholesale wholesale = wholesaleMapper.selectOne(Wrappers.<BatchWholesale>lambdaQuery()
                        .eq(BatchWholesale::getBatchId, downstream.getId()).last("LIMIT 1"));
                if (wholesale != null) {
                    builder.wholesaleDate(wholesale.getWholesaleDate())
                            .inboundTemp(wholesale.getInboundTemp())
                            .coldStorageTemp(wholesale.getColdStorageTemp())
                            .outboundTemp(wholesale.getOutboundTemp());
                }
            } else if (type == Constants.ENTERPRISE_TYPE_RETAIL) {
                BatchRetail retail = batchService.loadRetailEntity(downstream.getId());
                if (retail != null) {
                    builder.displayTemp(retail.getDisplayTemp()).saleStore(retail.getSaleStore());
                }
            }
        }
        return builder.build();
    }

    private UpstreamBatchSimpleVO buildUpstreamBatch(ProductBatch upstream, BatchConfirmRequest request) {
        if (upstream == null) {
            return null;
        }
        return UpstreamBatchSimpleVO.builder()
                .batchId(upstream.getId())
                .batchNo(upstream.getBatchNo())
                .productVariety(upstream.getProductVariety())
                .handoverTemp(upstream.getHandoverTemp())
                .publishTime(upstream.getPublishTime())
                .build();
    }

    // ==================================================================
    // 10.9 发送确认请求
    // ==================================================================

    /**
     * 向上游企业发送进场确认请求（接口 10.9）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ConfirmRequestSendVO send(LoginUser user, Long batchId) {
        if (user.getEnterpriseType() != null
                && user.getEnterpriseType() == Constants.ENTERPRISE_TYPE_FISHING) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "捕捞与养殖企业处于链条源头，无上游企业");
        }
        ProductBatch batch = batchService.requireOwnedBatch(user, batchId);

        // 校验顺序很关键：必须"先判重复请求、再判批号状态"。
        // 接口文档 10.9 业务规则 4 与附录 G 第 8 条要求：
        // 对已处于"待确认"的批号再次发送时返回 409（而不是 2005），
        // 因为此时批号状态与"存在待确认请求"是同一件事的两种描述，
        // 409 才能准确告知前端"该批号已存在待确认的进场确认请求"。
        BatchConfirmRequest existing = confirmRequestMapper.selectOne(Wrappers.<BatchConfirmRequest>lambdaQuery()
                .eq(BatchConfirmRequest::getBatchId, batchId).last("LIMIT 1"));
        if (existing != null && existing.getRequestStatus() != null
                && existing.getRequestStatus() == Constants.REQUEST_STATUS_PENDING) {
            throw BusinessException.of(ErrorCode.CONFLICT, "该批号已存在待确认的进场确认请求");
        }

        if (batch.getBatchStatus() != Constants.BATCH_STATUS_NEW) {
            throw BusinessException.of(ErrorCode.BATCH_STATUS_NOT_ALLOWED, "仅新建状态的批号可以发送确认请求");
        }
        if (batch.getUpstreamEnterpriseId() == null || batch.getUpstreamBatchId() == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "请先填写上游企业与上游产品批号");
        }
        // 上游批号必须仍处于已发布状态
        ProductBatch upstream = batchMapper.selectById(batch.getUpstreamBatchId());
        if (upstream == null
                || upstream.getBatchStatus() == null
                || upstream.getBatchStatus() != Constants.BATCH_STATUS_CONFIRMED) {
            throw BusinessException.of(ErrorCode.UPSTREAM_BATCH_INVALID);
        }

        String requestNo = batchService.submitConfirmRequest(user, batch, batch.getHandoverTemp());

        BatchConfirmRequest saved = confirmRequestMapper.selectOne(Wrappers.<BatchConfirmRequest>lambdaQuery()
                .eq(BatchConfirmRequest::getBatchId, batchId).last("LIMIT 1"));

        return ConfirmRequestSendVO.builder()
                .requestId(saved == null ? null : saved.getId())
                .requestNo(requestNo)
                .requestStatus(Constants.REQUEST_STATUS_PENDING)
                .requestStatusName(dictService.requestStatusName(Constants.REQUEST_STATUS_PENDING))
                .batchId(batch.getId())
                .batchNo(batch.getBatchNo())
                .batchStatus(Constants.BATCH_STATUS_PENDING)
                .batchStatusName(dictService.batchStatusName(Constants.BATCH_STATUS_PENDING))
                .upstreamEnterpriseId(batch.getUpstreamEnterpriseId())
                .upstreamEnterpriseName(batch.getUpstreamEnterpriseName())
                .upstreamBatchNo(batch.getUpstreamBatchNo())
                .handoverTemp(batch.getHandoverTemp())
                .requestTime(saved == null ? LocalDateTime.now() : saved.getRequestTime())
                .build();
    }

    // ==================================================================
    // 11.3 确认进场
    // ==================================================================

    /**
     * 确认进场（接口 11.3）。
     *
     * <p>事务内依次完成：请求状态置 2、下游批号置 3、零售商生成溯源标识码。
     * 任一步失败整体回滚，避免出现"批号已确认但溯源码缺失"的不一致状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public ConfirmResultVO confirm(LoginUser user, Long id, String handleRemark) {
        requireConfirmableType(user.getEnterpriseType());
        BatchConfirmRequest request = confirmRequestMapper.selectById(id);
        if (request == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND, "确认请求不存在");
        }
        if (!request.getToEnterpriseId().equals(user.getEnterpriseId())) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "只有请求接收方可以确认进场");
        }

        LocalDateTime now = LocalDateTime.now();
        int affected = confirmRequestMapper.confirmIfPending(id, user.getEnterpriseId(), now, handleRemark);
        if (affected == 0) {
            throw BusinessException.of(ErrorCode.CONFLICT, "该请求已被处理，请刷新后重试");
        }

        int batchAffected = batchMapper.confirmIfPending(request.getBatchId(), now);
        if (batchAffected == 0) {
            throw BusinessException.of(ErrorCode.CONFLICT, "下游批号状态异常，无法确认");
        }

        ProductBatch downstream = batchMapper.selectById(request.getBatchId());
        TraceCodeInfoVO traceCodeInfo = null;
        if (downstream != null
                && downstream.getEnterpriseType() != null
                && downstream.getEnterpriseType() == Constants.ENTERPRISE_TYPE_RETAIL) {
            NodeEnterprise retailer = enterpriseMapper.selectById(downstream.getEnterpriseId());
            BatchRetail retail = batchService.loadRetailEntity(downstream.getId());
            TraceCode traceCode = traceCodeService.generateForBatch(downstream, retail,
                    retailer == null ? null : retailer.getProvinceCode());
            traceCodeInfo = TraceCodeInfoVO.builder()
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
                    .build();
            log.info("零售商批号确认进场并生成溯源标识码: batchId={} traceCode={}",
                    downstream.getId(), traceCode.getTraceCode());
        }

        return ConfirmResultVO.builder()
                .requestId(request.getId())
                .requestNo(request.getRequestNo())
                .requestStatus(Constants.REQUEST_STATUS_CONFIRMED)
                .requestStatusName(dictService.requestStatusName(Constants.REQUEST_STATUS_CONFIRMED))
                .handleTime(now)
                .batchId(request.getBatchId())
                .batchNo(request.getBatchNo())
                .batchStatus(Constants.BATCH_STATUS_CONFIRMED)
                .batchStatusName(dictService.batchStatusName(Constants.BATCH_STATUS_CONFIRMED))
                .traceCode(traceCodeInfo)
                .build();
    }

    // ==================================================================
    // 11.4 拒绝进场（扩展）
    // ==================================================================

    /**
     * 拒绝进场（接口 11.4）：下游批号回退为"新建"，允许修正后重新发起。
     */
    @Transactional(rollbackFor = Exception.class)
    public RejectResultVO reject(LoginUser user, Long id, String handleRemark) {
        requireConfirmableType(user.getEnterpriseType());
        BatchConfirmRequest request = confirmRequestMapper.selectById(id);
        if (request == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND, "确认请求不存在");
        }
        if (!request.getToEnterpriseId().equals(user.getEnterpriseId())) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "只有请求接收方可以拒绝进场");
        }

        LocalDateTime now = LocalDateTime.now();
        int affected = confirmRequestMapper.rejectIfPending(id, user.getEnterpriseId(), now, handleRemark);
        if (affected == 0) {
            throw BusinessException.of(ErrorCode.CONFLICT, "该请求已被处理，请刷新后重试");
        }

        int batchAffected = batchMapper.revertToNewIfPending(request.getBatchId());
        if (batchAffected == 0) {
            throw BusinessException.of(ErrorCode.CONFLICT, "下游批号状态异常，无法回退");
        }

        return RejectResultVO.builder()
                .requestId(request.getId())
                .requestStatus(Constants.REQUEST_STATUS_REJECTED)
                .requestStatusName(dictService.requestStatusName(Constants.REQUEST_STATUS_REJECTED))
                .batchId(request.getBatchId())
                .batchStatus(Constants.BATCH_STATUS_NEW)
                .batchStatusName(dictService.batchStatusName(Constants.BATCH_STATUS_NEW))
                .build();
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    /**
     * 零售商（类型 4）处于链条末端，不参与"下游企业进场确认"。
     */
    private void requireConfirmableType(Integer enterpriseType) {
        if (enterpriseType == null || enterpriseType == Constants.ENTERPRISE_TYPE_RETAIL) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "零售商处于链条末端，无需处理下游进场确认");
        }
    }

    private BatchConfirmRequest requireVisibleRequest(LoginUser user, Long id) {
        BatchConfirmRequest request = confirmRequestMapper.selectById(id);
        if (request == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND, "确认请求不存在");
        }
        boolean isReceiver = request.getToEnterpriseId().equals(user.getEnterpriseId());
        boolean isSender = request.getFromEnterpriseId().equals(user.getEnterpriseId());
        if (!isReceiver && !isSender) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "无权查看该确认请求");
        }
        return request;
    }
}
