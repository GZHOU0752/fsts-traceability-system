package com.fsts.trace.service.support;

import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.Constants;
import com.fsts.trace.common.ErrorCode;
import com.fsts.trace.dto.request.BatchSaveRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 冷链温度判定（接口文档附录 F）。
 *
 * <p>判定规则：
 * 1. 单个环节内所有温度项均达标才判定合格，任一项超限即 coldChainOk 置 0；
 * 2. handoverTemp 取本环节代表温度：捕捞取起运、加工取出厂、批发取出库、零售取陈列；
 * 3. 超限时返回 2004 要求前端二次确认；携带 forceSubmit 为 true 则允许保存并标记为冷链异常。
 */
@Component
public class ColdChainService {

    /**
     * 判定结果：代表温度、本环节是否合格、超限说明列表。
     */
    public record ColdChainResult(BigDecimal handoverTemp, boolean coldChainOk, List<String> violations) {
    }

    /**
     * 按企业类型判定温度。
     */
    public ColdChainResult judge(Integer enterpriseType, BatchSaveRequest request) {
        if (enterpriseType == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "企业类型不能为空");
        }
        List<String> violations = new ArrayList<>();
        BigDecimal handoverTemp;

        switch (enterpriseType) {
            case Constants.ENTERPRISE_TYPE_FISHING -> {
                handoverTemp = request.getDepartureTemp();
                checkNotExceed(violations, "起运温度", handoverTemp, Constants.TEMP_THRESHOLD_NORMAL);
            }
            case Constants.ENTERPRISE_TYPE_PROCESSING -> {
                checkNotExceed(violations, "速冻中心温度", request.getQuickFreezeTemp(),
                        Constants.TEMP_THRESHOLD_QUICK_FREEZE);
                // 代表温度取出厂温度
                handoverTemp = request.getFactoryTemp();
                checkNotExceed(violations, "出厂温度", handoverTemp, Constants.TEMP_THRESHOLD_NORMAL);
            }
            case Constants.ENTERPRISE_TYPE_WHOLESALE -> {
                checkNotExceed(violations, "入库温度", request.getInboundTemp(), Constants.TEMP_THRESHOLD_NORMAL);
                checkNotExceed(violations, "冷库温度", request.getColdStorageTemp(), Constants.TEMP_THRESHOLD_NORMAL);
                handoverTemp = request.getOutboundTemp();
                checkNotExceed(violations, "出库温度", handoverTemp, Constants.TEMP_THRESHOLD_NORMAL);
            }
            case Constants.ENTERPRISE_TYPE_RETAIL -> {
                handoverTemp = request.getDisplayTemp();
                checkNotExceed(violations, "冷冻陈列柜温度", handoverTemp, Constants.TEMP_THRESHOLD_NORMAL);
            }
            default -> throw BusinessException.of(ErrorCode.PARAM_INVALID, "企业类型取值必须为 1~4");
        }

        boolean ok = violations.isEmpty();
        return new ColdChainResult(handoverTemp, ok, violations);
    }

    /**
     * 超限校验：未强制提交时抛出 2004，让前端弹窗二次确认后重新提交。
     */
    public void assertAcceptable(ColdChainResult result, Boolean forceSubmit) {
        if (result.coldChainOk() || Boolean.TRUE.equals(forceSubmit)) {
            return;
        }
        throw BusinessException.of(ErrorCode.COLD_CHAIN_ABNORMAL,
                "冷链温度异常：" + String.join("；", result.violations()) + "，确认后仍可提交但会标记为冷链异常");
    }

    /**
     * 温度项是否达标：要求不高于阈值（冷链温度越低越安全）。
     */
    public static boolean isQualified(BigDecimal value, BigDecimal threshold) {
        return value != null && value.compareTo(threshold) <= 0;
    }

    private void checkNotExceed(List<String> violations, String name, BigDecimal value, BigDecimal threshold) {
        if (value == null) {
            return;
        }
        if (value.compareTo(threshold) > 0) {
            violations.add(name + " " + value.setScale(1, RoundingMode.HALF_UP)
                    + " ℃ 高于阈值 " + threshold.setScale(0, RoundingMode.HALF_UP) + " ℃");
        }
    }
}
