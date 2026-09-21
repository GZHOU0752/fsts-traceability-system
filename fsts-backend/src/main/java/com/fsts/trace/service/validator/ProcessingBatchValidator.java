package com.fsts.trace.service.validator;

import com.fsts.trace.common.Constants;
import com.fsts.trace.dto.request.BatchSaveRequest;
import org.springframework.stereotype.Component;

/**
 * 类型 2（冷冻加工企业）批号校验。
 *
 * <p>必填：上游企业与上游批号、加工形态、出厂检验报告编号、速冻中心温度、出厂温度。
 * 产品品种与来源类型由上游带出，不在请求体强制要求。
 */
@Component
public class ProcessingBatchValidator implements BatchValidator {

    @Override
    public int supportType() {
        return Constants.ENTERPRISE_TYPE_PROCESSING;
    }

    @Override
    public void validate(BatchSaveRequest request) {
        RequiredFieldChecks.notNull(request.getUpstreamEnterpriseId(), "上游企业");
        RequiredFieldChecks.notNull(request.getUpstreamBatchId(), "上游产品批号");
        RequiredFieldChecks.notBlank(request.getProcessForm(), "加工形态");
        RequiredFieldChecks.notLongerThan(request.getProcessForm(), 30, "加工形态");
        RequiredFieldChecks.notBlank(request.getInspectionNo(), "出厂检验报告编号");
        RequiredFieldChecks.notLongerThan(request.getInspectionNo(), 60, "出厂检验报告编号");
        RequiredFieldChecks.notNull(request.getQuickFreezeTemp(), "速冻中心温度");
        RequiredFieldChecks.notNull(request.getFactoryTemp(), "出厂温度");
        RequiredFieldChecks.notLongerThan(request.getProductionBatchNo(), 60, "生产批次记录编号");
    }
}
