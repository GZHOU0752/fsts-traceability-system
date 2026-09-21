package com.fsts.trace.service.support;

import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.Constants;
import com.fsts.trace.common.ErrorCode;
import com.fsts.trace.dto.request.EnterpriseCreateRequest;
import com.fsts.trace.dto.request.EnterpriseSaveRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 企业资质字段的按类型校验器（接口 6.3 的附加资质字段表）。
 *
 * <p>为什么用集中式校验而不是在每个字段上打注解：
 * 必填规则取决于"企业类型"这个运行时值（类型 1 要求捕捞证或养殖证之一，
 * 类型 2 要求生产许可证，类型 3/4 要求经营许可证），
 * 注解无法表达这种跨字段的条件必填，硬写会退化成在每个分支里重复 if。
 */
@Component
public class EnterpriseValidator {

    /**
     * 新建时的资质校验。
     */
    public void validateCreate(EnterpriseCreateRequest request) {
        validateByType(request.getEnterpriseType(), request);
    }

    /**
     * 更新时的资质校验（类型以数据库中的既有值为准，不允许通过入参改变）。
     */
    public void validateUpdate(Integer enterpriseType, EnterpriseSaveRequest request) {
        validateByType(enterpriseType, request);
    }

    private void validateByType(Integer enterpriseType, EnterpriseSaveRequest request) {
        if (enterpriseType == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "企业类型不能为空");
        }
        switch (enterpriseType) {
            case Constants.ENTERPRISE_TYPE_FISHING -> {
                // 海洋捕捞与水产养殖至少具备其一，允许兼营
                if (!StringUtils.hasText(request.getFisheryLicenseNo())
                        && !StringUtils.hasText(request.getAquacultureLicenseNo())) {
                    throw BusinessException.of(ErrorCode.PARAM_INVALID,
                            "捕捞与养殖企业须至少填写渔业捕捞许可证编号或水域滩涂养殖证编号");
                }
            }
            case Constants.ENTERPRISE_TYPE_PROCESSING -> {
                if (!StringUtils.hasText(request.getFoodProductionLicenseNo())) {
                    throw BusinessException.of(ErrorCode.PARAM_INVALID, "冷冻加工企业须填写食品生产许可证编号");
                }
            }
            case Constants.ENTERPRISE_TYPE_WHOLESALE, Constants.ENTERPRISE_TYPE_RETAIL -> {
                if (!StringUtils.hasText(request.getFoodBusinessLicenseNo())) {
                    throw BusinessException.of(ErrorCode.PARAM_INVALID, "该类型企业须填写食品经营许可证编号");
                }
            }
            default -> throw BusinessException.of(ErrorCode.PARAM_INVALID, "企业类型取值必须为 1~4");
        }
    }

    /**
     * 剔除不属于该企业类型的资质字段。
     *
     * <p>接口文档 6.3 业务规则 4：传入了不属于该类型的资质字段时忽略且不报错。
     */
    public void clearIrrelevantLicenses(Integer enterpriseType, com.fsts.trace.entity.NodeEnterprise entity) {
        if (enterpriseType == null) {
            return;
        }
        if (enterpriseType != Constants.ENTERPRISE_TYPE_FISHING) {
            entity.setFisheryLicenseNo(null);
            entity.setAquacultureLicenseNo(null);
            entity.setFryLicenseNo(null);
        }
        if (enterpriseType != Constants.ENTERPRISE_TYPE_PROCESSING) {
            entity.setFoodProductionLicenseNo(null);
            entity.setExportFilingNo(null);
        }
        boolean usesFoodBusinessLicense = enterpriseType == Constants.ENTERPRISE_TYPE_WHOLESALE
                || enterpriseType == Constants.ENTERPRISE_TYPE_RETAIL;
        if (!usesFoodBusinessLicense) {
            entity.setFoodBusinessLicenseNo(null);
        }
        if (enterpriseType != Constants.ENTERPRISE_TYPE_WHOLESALE) {
            entity.setRoadTransportLicenseNo(null);
            entity.setColdStorageCapacity(null);
            entity.setTransportToolInfo(null);
        }
        if (enterpriseType != Constants.ENTERPRISE_TYPE_RETAIL) {
            entity.setDisplayEquipmentInfo(null);
        }
    }
}
