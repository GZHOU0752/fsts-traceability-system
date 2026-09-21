package com.fsts.trace.service.validator;

import com.fsts.trace.common.Constants;
import com.fsts.trace.dto.request.BatchSaveRequest;
import org.springframework.stereotype.Component;

/**
 * 类型 4（零售商）批号校验。
 *
 * <p>必填：上游企业与上游批号、上架日期、冷冻陈列柜温度、销售门店。
 */
@Component
public class RetailBatchValidator implements BatchValidator {

    @Override
    public int supportType() {
        return Constants.ENTERPRISE_TYPE_RETAIL;
    }

    @Override
    public void validate(BatchSaveRequest request) {
        RequiredFieldChecks.notNull(request.getUpstreamEnterpriseId(), "上游企业");
        RequiredFieldChecks.notNull(request.getUpstreamBatchId(), "上游产品批号");
        RequiredFieldChecks.notNull(request.getShelfDate(), "上架日期");
        RequiredFieldChecks.notNull(request.getDisplayTemp(), "冷冻陈列柜温度");
        RequiredFieldChecks.notBlank(request.getSaleStore(), "销售门店");
        RequiredFieldChecks.notLongerThan(request.getSaleStore(), 100, "销售门店");
        RequiredFieldChecks.notLongerThan(request.getDisplayEquipNo(), 50, "陈列设备编号");
    }
}
