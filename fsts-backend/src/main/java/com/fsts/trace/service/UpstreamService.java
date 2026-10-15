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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * <p><b>为什么必须校验"上游企业类型"：</b>查询接口（9.1/9.2）虽然按当前企业类型过滤了
     * 上游企业，但那只约束了界面下拉框；如果不在写入路径再做一次类型比对，
     * 直接调用 10.3 的客户端就能让零售商挂到捕捞企业的批号上，
     * 造出"捕捞 → 零售"的断链，而消费者端仍会显示"冷链未断链"（见 12.1 结论文案）。
     *
     * @param currentEnterpriseType 当前登录企业的环节类型（1~4）
     * @return 校验通过的上游批号实体（供"上游带出"使用）
     */
    public UpstreamSelection requireAvailableUpstreamBatch(Integer currentEnterpriseType,
                                                           Long upstreamEnterpriseId,
                                                           Long upstreamBatchId) {
        if (upstreamEnterpriseId == null || upstreamBatchId == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "请先选择上游企业与上游产品批号");
        }

        Integer expectedType = resolveUpstreamType(currentEnterpriseType);
        if (expectedType == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "当前环节处于溯源链源头，没有上游企业");
        }

        NodeEnterprise upstreamEnterprise = enterpriseMapper.selectById(upstreamEnterpriseId);
        if (upstreamEnterprise == null
                || upstreamEnterprise.getStatus() == null
                || upstreamEnterprise.getStatus() != Constants.STATUS_ENABLED) {
            throw BusinessException.of(ErrorCode.UPSTREAM_BATCH_INVALID);
        }

        // 链条必须逐级衔接：加工上游只能是捕捞、批发上游只能是加工、零售上游只能是批发
        if (!expectedType.equals(upstreamEnterprise.getEnterpriseType())) {
            // 与 9.2 查询接口保持同一口径（同一句提示、同一个 400），避免前后端两套说法
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "所选企业不是本环节的上游企业类型，请按\"捕捞与养殖 → 冷冻加工 → 批发 → 零售\"的顺序选择");
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

    /**
     * 一次性加载本企业可用的上游批号索引（key = 上游批号，value = 候选批次）。
     *
     * <p>批量导入时逐行查询上游批号会产生 N 次数据库往返；这里把"本环节合法的上游企业"
     * 与其已发布批号一次性取出，导入过程变成纯内存匹配，无论多少行都只查两次库。
     *
     * <p>返回"批号 → 候选列表"而不是一对一映射：不同上游企业完全可能使用同名批号
     * （各家编码规则互不相同），冲突时由调用方要求用户补充企业名称。
     */
    public Map<String, List<UpstreamSelection>> indexAvailableUpstreamBatches(Integer currentEnterpriseType) {
        Integer upstreamType = resolveUpstreamType(currentEnterpriseType);
        if (upstreamType == null) {
            return Map.of();
        }
        List<NodeEnterprise> enterprises = enterpriseMapper.selectList(Wrappers.<NodeEnterprise>lambdaQuery()
                .eq(NodeEnterprise::getEnterpriseType, upstreamType)
                .eq(NodeEnterprise::getStatus, Constants.STATUS_ENABLED));
        if (enterprises.isEmpty()) {
            return Map.of();
        }
        Map<Long, NodeEnterprise> enterpriseById = new HashMap<>(enterprises.size() * 2);
        List<Long> enterpriseIds = new ArrayList<>(enterprises.size());
        for (NodeEnterprise enterprise : enterprises) {
            enterpriseById.put(enterprise.getId(), enterprise);
            enterpriseIds.add(enterprise.getId());
        }

        List<ProductBatch> batches = batchMapper.selectList(Wrappers.<ProductBatch>lambdaQuery()
                .in(ProductBatch::getEnterpriseId, enterpriseIds)
                .eq(ProductBatch::getBatchStatus, Constants.BATCH_STATUS_CONFIRMED)
                .eq(ProductBatch::getDeleted, 0));
        Map<String, List<UpstreamSelection>> index = new HashMap<>(batches.size() * 2);
        for (ProductBatch batch : batches) {
            index.computeIfAbsent(batch.getBatchNo(), key -> new ArrayList<>(2))
                    .add(new UpstreamSelection(batch, enterpriseById.get(batch.getEnterpriseId())));
        }
        return index;
    }

    /**
     * 按批号（+ 可选企业名称）解析唯一的上游批号。
     *
     * @throws BusinessException 查不到（2003），或存在多个同名批号又未指定企业时抛出
     */
    public UpstreamSelection resolveUpstreamByBatchNo(Map<String, List<UpstreamSelection>> index,
                                                      String batchNo, String enterpriseName) {
        if (!StringUtils.hasText(batchNo)) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "请填写上游产品批号");
        }
        List<UpstreamSelection> candidates = index.get(batchNo.trim());
        if (candidates == null || candidates.isEmpty()) {
            throw BusinessException.of(ErrorCode.UPSTREAM_BATCH_INVALID,
                    "上游批号 " + batchNo + " 不存在或尚未发布（只有已确认的批号才能被选择）");
        }
        if (StringUtils.hasText(enterpriseName)) {
            for (UpstreamSelection candidate : candidates) {
                if (enterpriseName.trim().equals(candidate.enterprise().getEnterpriseName())) {
                    return candidate;
                }
            }
            throw BusinessException.of(ErrorCode.UPSTREAM_BATCH_INVALID,
                    "上游批号 " + batchNo + " 不属于企业「" + enterpriseName + "」");
        }
        if (candidates.size() > 1) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "上游批号 " + batchNo + " 在多家上游企业中存在，请补充上游企业名称");
        }
        return candidates.get(0);
    }

    private Boolean isColdChainOk(ProductBatch batch) {
        return batch.getColdChainOk() == null || batch.getColdChainOk() == 1;
    }
}
