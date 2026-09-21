package com.fsts.trace.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.Constants;
import com.fsts.trace.common.ErrorCode;
import com.fsts.trace.common.LoginUser;
import com.fsts.trace.dto.query.UpstreamBatchQuery;
import com.fsts.trace.dto.query.UpstreamEnterpriseQuery;
import com.fsts.trace.entity.NodeEnterprise;
import com.fsts.trace.entity.ProductBatch;
import com.fsts.trace.mapper.NodeEnterpriseMapper;
import com.fsts.trace.mapper.ProductBatchMapper;
import com.fsts.trace.vo.PageVO;
import com.fsts.trace.vo.UpstreamBatchVO;
import com.fsts.trace.vo.UpstreamEnterpriseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 流通节点端 - 上游数据选择（接口 9.1 / 9.2）。
 *
 * <p>溯源链是严格线性的：捕捞与养殖 -> 冷冻加工 -> 批发 -> 零售。
 * 因此"上游企业类型"完全由当前登录企业的类型唯一确定，
 * 前端无需传企业类型参数，也避免了用户跨环节乱选（例如零售商直接选捕捞企业）。
 */
@Slf4j
@Service
public class UpstreamService {

    private final NodeEnterpriseMapper enterpriseMapper;
    private final ProductBatchMapper batchMapper;
    private final DictService dictService;

    public UpstreamService(NodeEnterpriseMapper enterpriseMapper,
                           ProductBatchMapper batchMapper,
                           DictService dictService) {
        this.enterpriseMapper = enterpriseMapper;
        this.batchMapper = batchMapper;
        this.dictService = dictService;
    }

    /**
     * 由当前企业类型推导上游企业类型。
     *
     * @return 上游企业类型；当前企业为链条源头（类型 1）时返回 null
     */
    public static Integer resolveUpstreamType(Integer currentType) {
        if (currentType == null) {
            return null;
        }
        return switch (currentType) {
            case Constants.ENTERPRISE_TYPE_PROCESSING -> Constants.ENTERPRISE_TYPE_FISHING;
            case Constants.ENTERPRISE_TYPE_WHOLESALE -> Constants.ENTERPRISE_TYPE_PROCESSING;
            case Constants.ENTERPRISE_TYPE_RETAIL -> Constants.ENTERPRISE_TYPE_WHOLESALE;
            default -> null;
        };
    }

    /**
     * 查询上游企业列表（接口 9.1）。
     */
    public PageVO<UpstreamEnterpriseVO> listUpstreamEnterprises(LoginUser user, UpstreamEnterpriseQuery query) {
        Integer upstreamType = resolveUpstreamType(user.getEnterpriseType());
        if (upstreamType == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "捕捞与养殖企业处于链条源头，没有上游企业");
        }

        Page<NodeEnterprise> page = new Page<>(query.safeCurrent(), query.safeSize());
        IPage<NodeEnterprise> result = enterpriseMapper.selectPage(page, Wrappers.<NodeEnterprise>lambdaQuery()
                .eq(NodeEnterprise::getEnterpriseType, upstreamType)
                .eq(NodeEnterprise::getStatus, Constants.STATUS_ENABLED)
                .eq(StringUtils.hasText(query.getProvinceCode()), NodeEnterprise::getProvinceCode, query.getProvinceCode())
                .eq(StringUtils.hasText(query.getCityCode()), NodeEnterprise::getCityCode, query.getCityCode())
                .like(StringUtils.hasText(query.getEnterpriseName()), NodeEnterprise::getEnterpriseName, query.getEnterpriseName())
                .orderByAsc(NodeEnterprise::getProvinceCode)
                .orderByAsc(NodeEnterprise::getCityCode)
                .orderByAsc(NodeEnterprise::getId));

        return PageVO.of(result, e -> UpstreamEnterpriseVO.builder()
                .id(e.getId())
                .enterpriseName(e.getEnterpriseName())
                .enterpriseType(e.getEnterpriseType())
                .enterpriseTypeName(dictService.enterpriseTypeName(e.getEnterpriseType()))
                .provinceCode(e.getProvinceCode())
                .provinceName(e.getProvinceName())
                .cityCode(e.getCityCode())
                .cityName(e.getCityName())
                .address(e.getAddress())
                .build());
    }

    /**
     * 查询上游企业已发布的产品批号（接口 9.2）。
     *
     * <p>只返回 batchStatus = 3（已确认/已发布）的批号：
     * 新建(1) 尚未对外可用，待确认(2) 尚未通过上游确认，已下架(4) 不可被选择。
     */
    public PageVO<UpstreamBatchVO> listUpstreamBatches(LoginUser user, UpstreamBatchQuery query) {
        Integer upstreamType = resolveUpstreamType(user.getEnterpriseType());
        if (upstreamType == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "捕捞与养殖企业处于链条源头，没有上游企业");
        }

        NodeEnterprise upstream = enterpriseMapper.selectById(query.getUpstreamEnterpriseId());
        if (upstream == null || upstream.getStatus() == null
                || upstream.getStatus() != Constants.STATUS_ENABLED) {
            throw BusinessException.of(ErrorCode.NOT_FOUND, "上游企业不存在或已停用");
        }
        if (!upstreamType.equals(upstream.getEnterpriseType())) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "所选企业不是本环节的上游企业类型");
        }

        Page<ProductBatch> page = new Page<>(query.safeCurrent(), query.safeSize());
        IPage<ProductBatch> result = batchMapper.selectPage(page, Wrappers.<ProductBatch>lambdaQuery()
                .eq(ProductBatch::getEnterpriseId, query.getUpstreamEnterpriseId())
                .eq(ProductBatch::getBatchStatus, Constants.BATCH_STATUS_CONFIRMED)
                .eq(ProductBatch::getDeleted, 0)
                .like(StringUtils.hasText(query.getBatchNo()), ProductBatch::getBatchNo, query.getBatchNo())
                .orderByDesc(ProductBatch::getPublishTime)
                .orderByDesc(ProductBatch::getId));

        return PageVO.of(result, b -> UpstreamBatchVO.builder()
                .id(b.getId())
                .batchNo(b.getBatchNo())
                .productVariety(b.getProductVariety())
                .sourceType(b.getSourceType())
                .sourceTypeName(dictService.sourceTypeName(b.getSourceType()))
                .enterpriseName(b.getEnterpriseName())
                .publishTime(b.getPublishTime())
                .handoverTemp(b.getHandoverTemp())
                .coldChainOk(isColdChainOk(b))
                .build());
    }

    /**
     * 校验上游批号可用性（新建/更新批号时调用）。
     *
     * <p>接口 9.2 业务规则 3：若上游批号在提交时已被下架，返回 code = 2003。
     *
     * @return 校验通过的上游批号实体（供"上游带出"使用）
     */
    public UpstreamSelection requireAvailableUpstreamBatch(Long upstreamEnterpriseId, Long upstreamBatchId) {
        if (upstreamEnterpriseId == null || upstreamBatchId == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "请先选择上游企业与上游产品批号");
        }
        NodeEnterprise upstreamEnterprise = enterpriseMapper.selectById(upstreamEnterpriseId);
        if (upstreamEnterprise == null
                || upstreamEnterprise.getStatus() == null
                || upstreamEnterprise.getStatus() != Constants.STATUS_ENABLED) {
            throw BusinessException.of(ErrorCode.UPSTREAM_BATCH_INVALID);
        }

        ProductBatch upstreamBatch = batchMapper.selectOne(Wrappers.<ProductBatch>lambdaQuery()
                .eq(ProductBatch::getId, upstreamBatchId)
                .eq(ProductBatch::getDeleted, 0)
                .last("LIMIT 1"));

        if (upstreamBatch == null
                || upstreamBatch.getBatchStatus() == null
                || upstreamBatch.getBatchStatus() != Constants.BATCH_STATUS_CONFIRMED
                || !upstreamEnterpriseId.equals(upstreamBatch.getEnterpriseId())) {
            throw BusinessException.of(ErrorCode.UPSTREAM_BATCH_INVALID);
        }
        return new UpstreamSelection(upstreamBatch, upstreamEnterprise);
    }

    /**
     * 上游选择结果：上游批号 + 上游企业。
     *
     * <p>为什么需要带上企业实体：批次表冗余的是"上游企业的上游"信息，
     * 而下游批号要记录的是"直接上游企业的所在省市"，
     * 该字段只能从企业表取，不能从上游批号上取。
     */
    public record UpstreamSelection(ProductBatch batch, NodeEnterprise enterprise) {
    }

    private Boolean isColdChainOk(ProductBatch batch) {
        return batch.getColdChainOk() == null || batch.getColdChainOk() == 1;
    }
}
