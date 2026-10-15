package com.fsts.trace.service;

import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.LoginUser;
import com.fsts.trace.dto.request.BatchImportRequest;
import com.fsts.trace.dto.request.BatchImportRow;
import com.fsts.trace.vo.BatchImportResultVO;
import com.fsts.trace.vo.BatchSaveResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 产品批号批量导入（接口 10.12）。
 *
 * <p><b>为什么需要：</b>系统的定位是"低侵入式"接入——企业本来就有渔船管理、生产批次、
 * 进销存系统，让他们在页面上一条条手工录入，恰恰违背了这个定位。批量导入把
 * "从原有系统导出 → 粘贴/上传 → 一次性入库"变成可行路径。
 *
 * <p><b>事务边界：</b>本方法本身<b>不加</b> {@code @Transactional}，逐行调用
 * {@link EnterpriseBatchService#create}（它自带事务），因此：
 * <ul>
 *   <li>一行失败不会回滚已成功的行，符合"几十行里错两三行"的真实场景；</li>
 *   <li>失败行自己那一次事务完整回滚，不会留下"主表有、明细缺"的半成品。</li>
 * </ul>
 *
 * <p><b>上游解析：</b>导入文件里只有上游批号（来自对方单据），因此先用
 * {@link UpstreamService#indexAvailableUpstreamBatches} 建索引，再按批号解析成 ID，
 * 全程只查两次库。
 */
@Slf4j
@Service
public class EnterpriseBatchImportService {

    private final EnterpriseBatchService batchService;
    private final UpstreamService upstreamService;

    public EnterpriseBatchImportService(EnterpriseBatchService batchService,
                                        UpstreamService upstreamService) {
        this.batchService = batchService;
        this.upstreamService = upstreamService;
    }

    /**
     * 批量导入：逐行独立事务，返回每一行的结果。
     */
    public BatchImportResultVO importBatches(LoginUser user, BatchImportRequest request) {
        List<BatchImportRow> rows = request.getRows();
        int success = 0;
        List<BatchImportResultVO.Item> items = new ArrayList<>(rows.size());
        // 上游索引只查一次，供全部行复用
        Map<String, List<UpstreamService.UpstreamSelection>> upstreamIndex =
                upstreamService.indexAvailableUpstreamBatches(user.getEnterpriseType());

        int index = 0;
        for (BatchImportRow row : rows) {
            index++;
            int rowNo = row.getRowNo() == null ? index : row.getRowNo();
            try {
                // 逐条录入时由 @Valid 兜住批号非空，导入走的是服务层直调，必须自己校验
                if (!StringUtils.hasText(row.getBatchNo())) {
                    throw BusinessException.of(com.fsts.trace.common.ErrorCode.PARAM_INVALID, "产品批号不能为空");
                }
                applyUpstream(row, upstreamIndex);
                BatchSaveResultVO saved = batchService.create(user, row);
                success++;
                items.add(BatchImportResultVO.Item.builder()
                        .rowNo(rowNo)
                        .batchNo(saved.getBatchNo())
                        .success(true)
                        .batchId(saved.getId() == null ? null : String.valueOf(saved.getId()))
                        .build());
            } catch (BusinessException e) {
                items.add(BatchImportResultVO.Item.builder()
                        .rowNo(rowNo)
                        .batchNo(row.getBatchNo())
                        .success(false)
                        .message(e.getMessage())
                        .build());
            } catch (Exception e) {
                log.warn("批量导入单行失败: rowNo={} batchNo={} err={}", rowNo, row.getBatchNo(), e.getMessage());
                items.add(BatchImportResultVO.Item.builder()
                        .rowNo(rowNo)
                        .batchNo(row.getBatchNo())
                        .success(false)
                        .message("该行导入失败，请检查数据后重试")
                        .build());
            }
        }

        return BatchImportResultVO.builder()
                .total(rows.size())
                .successCount(success)
                .failureCount(rows.size() - success)
                .items(items)
                .build();
    }

    /**
     * 把导入行里的"上游批号"解析成上游企业 ID + 上游批号 ID。
     *
     * <p>类型 1（链条源头）没有上游，忽略这两列即可。
     */
    private void applyUpstream(BatchImportRow row,
                               Map<String, List<UpstreamService.UpstreamSelection>> upstreamIndex) {
        if (!StringUtils.hasText(row.getUpstreamBatchNo())) {
            return;
        }
        if (upstreamIndex.isEmpty()) {
            throw BusinessException.of(com.fsts.trace.common.ErrorCode.PARAM_INVALID,
                    "当前环节处于溯源链源头，不需要填写上游批号");
        }
        UpstreamService.UpstreamSelection selection = upstreamService.resolveUpstreamByBatchNo(
                upstreamIndex, row.getUpstreamBatchNo(), row.getUpstreamEnterpriseName());
        row.setUpstreamEnterpriseId(selection.enterprise().getId());
        row.setUpstreamBatchId(selection.batch().getId());
    }
}
