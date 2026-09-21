package com.fsts.trace.service.validator;

import com.fsts.trace.common.Constants;
import com.fsts.trace.dto.request.BatchSaveRequest;
import org.springframework.stereotype.Component;

/**
 * 类型 3（批发商）批号校验。
 *
 * <p>必填：上游企业与上游批号、批发日期、入库/冷库/出库三个温度、运输工具编号。
 */
@Component
public class WholesaleBatchValidator implements BatchValidator {

    @Override
    public int supportType() {
        return Constants.ENTERPRISE_TYPE_WHOLESALE;
    }

    @Override
    public void validate(BatchSaveRequest request) {
        RequiredFieldChecks.notNull(request.getUpstreamEnterpriseId(), "上游企业");
        RequiredFieldChecks.notNull(request.getUpstreamBatchId(), "上游产品批号");
        RequiredFieldChecks.notNull(request.getWholesaleDate(), "批发日期");
        RequiredFieldChecks.notNull(request.getInboundTemp(), "入库温度");
        RequiredFieldChecks.notNull(request.getColdStorageTemp(), "冷库温度");
        RequiredFieldChecks.notNull(request.getOutboundTemp(), "出库温度");
        RequiredFieldChecks.notBlank(request.getTransportToolNo(), "运输工具编号");
        RequiredFieldChecks.notLongerThan(request.getTransportToolNo(), 50, "运输工具编号");
        RequiredFieldChecks.notLongerThan(request.getColdStorageNo(), 50, "冷库编号");
    }
}
