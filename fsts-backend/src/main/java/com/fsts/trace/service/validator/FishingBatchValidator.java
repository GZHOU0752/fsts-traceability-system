package com.fsts.trace.service.validator;

import com.fsts.trace.common.Constants;
import com.fsts.trace.dto.request.BatchSaveRequest;
import org.springframework.stereotype.Component;

/**
 * 类型 1（捕捞与养殖企业）批号校验。
 *
 * <p>必填：产品品种、来源类型、捕捞/养殖日期、证明类型、证明编号、起运温度。
 * 源头环节没有上游，因此 upstreamEnterpriseId / upstreamBatchId 不参与校验。
 */
@Component
public class FishingBatchValidator implements BatchValidator {

    @Override
    public int supportType() {
        return Constants.ENTERPRISE_TYPE_FISHING;
    }

    @Override
    public void validate(BatchSaveRequest request) {
        RequiredFieldChecks.notBlank(request.getProductVariety(), "产品品种");
        RequiredFieldChecks.inRange(request.getSourceType(), 1, 2, "来源类型");
        RequiredFieldChecks.notNull(request.getCatchBreedDate(), "捕捞或养殖日期");
        RequiredFieldChecks.inRange(request.getCertificateType(), 1, 2, "证明类型");
        RequiredFieldChecks.notBlank(request.getCertificateNo(), "证明编号");
        RequiredFieldChecks.notLongerThan(request.getCertificateNo(), 60, "证明编号");
        RequiredFieldChecks.notNull(request.getDepartureTemp(), "起运温度");
        RequiredFieldChecks.notLongerThan(request.getDrugReportNo(), 60, "药物残留检测报告编号");
        RequiredFieldChecks.notLongerThan(request.getFishingLogNo(), 60, "渔捞日志编号");
    }
}
