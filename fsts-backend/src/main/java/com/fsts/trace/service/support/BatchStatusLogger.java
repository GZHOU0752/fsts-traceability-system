package com.fsts.trace.service.support;

import com.fsts.trace.common.LoginUser;
import com.fsts.trace.entity.BatchStatusLog;
import com.fsts.trace.entity.ProductBatch;
import com.fsts.trace.mapper.BatchStatusLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 批号状态流转记录器。
 *
 * <p><b>为什么记录失败不能影响业务：</b>日志是"旁路证据"，不是业务主数据。
 * 如果因为日志表写入失败而导致发布/下架这类核心动作回滚，反而本末倒置，
 * 因此这里对异常只告警不抛出（写库失败时业务照常提交）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchStatusLogger {

    public static final String ACTION_CREATE = "新建";
    public static final String ACTION_PUBLISH = "发布";
    public static final String ACTION_SEND_REQUEST = "发送确认请求";
    public static final String ACTION_CONFIRM = "确认进场";
    public static final String ACTION_REJECT = "拒绝进场";
    public static final String ACTION_WITHDRAW = "撤回请求";
    public static final String ACTION_OFF_SHELF = "下架";
    public static final String ACTION_RESTORE = "恢复上架";

    private static final String TYPE_ENTERPRISE = "ENTERPRISE";
    private static final String TYPE_SYSTEM = "SYSTEM";

    private final BatchStatusLogMapper logMapper;

    /**
     * 记录一次状态流转。
     *
     * @param operator 操作主体；为 null 时记为 SYSTEM（例如定时任务、数据修复）
     */
    public void record(ProductBatch batch, Integer fromStatus, Integer toStatus,
                       String action, String remark, LoginUser operator) {
        if (batch == null || batch.getId() == null) {
            return;
        }
        BatchStatusLog entity = new BatchStatusLog();
        entity.setBatchId(batch.getId());
        entity.setBatchNo(batch.getBatchNo());
        entity.setEnterpriseId(batch.getEnterpriseId());
        entity.setEnterpriseName(batch.getEnterpriseName());
        entity.setFromStatus(fromStatus);
        entity.setToStatus(toStatus);
        entity.setAction(action);
        entity.setRemark(remark);
        if (operator == null) {
            entity.setOperatorType(TYPE_SYSTEM);
            entity.setOperatorName("系统");
        } else {
            entity.setOperatorType(operator.getUserType() == null ? TYPE_ENTERPRISE : operator.getUserType());
            entity.setOperatorId(operator.getUserId());
            entity.setOperatorName(operator.getEnterpriseName() != null
                    ? operator.getEnterpriseName() : operator.getLoginName());
        }
        try {
            logMapper.insert(entity);
        } catch (Exception e) {
            log.warn("批号状态日志写入失败（不影响业务）: batchId={} action={} err={}",
                    batch.getId(), action, e.getMessage());
        }
    }
}
