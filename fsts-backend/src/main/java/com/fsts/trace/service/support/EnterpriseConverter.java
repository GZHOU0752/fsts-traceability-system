package com.fsts.trace.service.support;

import com.fsts.trace.common.Constants;
import com.fsts.trace.entity.NodeEnterprise;
import com.fsts.trace.service.DictService;
import com.fsts.trace.vo.EnterpriseDetailVO;
import com.fsts.trace.vo.EnterpriseInfoVO;
import com.fsts.trace.vo.EnterpriseListItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 企业实体到各类 VO 的转换器。
 *
 * <p>核心规则：<b>资质字段按企业类型选择性返回</b>。
 * 接口文档 4.5 明确"返回字段随企业类型不同而变化，不属于该类型的资质字段返回 null"，
 * 因此这里不做"全字段照搬"，而是按类型白名单填充，避免把无关资质泄漏给前端。
 */
@Component
@RequiredArgsConstructor
public class EnterpriseConverter {

    private final DictService dictService;

    /**
     * 列表项（接口 6.1）：不含资质证件明细，控制响应体体积。
     */
    public EnterpriseListItemVO toListItem(NodeEnterprise e) {
        return EnterpriseListItemVO.builder()
                .id(e.getId())
                .enterpriseCode(e.getEnterpriseCode())
                .enterpriseName(e.getEnterpriseName())
                .enterpriseType(e.getEnterpriseType())
                .enterpriseTypeName(dictService.enterpriseTypeName(e.getEnterpriseType()))
                .creditCode(e.getCreditCode())
                .legalPerson(e.getLegalPerson())
                .contactPerson(e.getContactPerson())
                .contactPhone(e.getContactPhone())
                .provinceCode(e.getProvinceCode())
                .provinceName(e.getProvinceName())
                .cityCode(e.getCityCode())
                .cityName(e.getCityName())
                .address(e.getAddress())
                .status(e.getStatus())
                .statusName(statusName(e.getStatus()))
                .registerTime(e.getRegisterTime())
                .build();
    }

    /**
     * 详情（接口 6.2）：管理端可见全部资质字段。
     */
    public EnterpriseDetailVO toDetail(NodeEnterprise e) {
        return EnterpriseDetailVO.builder()
                .id(e.getId())
                .enterpriseCode(e.getEnterpriseCode())
                .enterpriseName(e.getEnterpriseName())
                .enterpriseType(e.getEnterpriseType())
                .enterpriseTypeName(dictService.enterpriseTypeName(e.getEnterpriseType()))
                .loginName(e.getLoginName())
                .creditCode(e.getCreditCode())
                .legalPerson(e.getLegalPerson())
                .contactPerson(e.getContactPerson())
                .contactPhone(e.getContactPhone())
                .provinceCode(e.getProvinceCode())
                .provinceName(e.getProvinceName())
                .cityCode(e.getCityCode())
                .cityName(e.getCityName())
                .address(e.getAddress())
                .fisheryLicenseNo(e.getFisheryLicenseNo())
                .aquacultureLicenseNo(e.getAquacultureLicenseNo())
                .fryLicenseNo(e.getFryLicenseNo())
                .foodProductionLicenseNo(e.getFoodProductionLicenseNo())
                .exportFilingNo(e.getExportFilingNo())
                .foodBusinessLicenseNo(e.getFoodBusinessLicenseNo())
                .roadTransportLicenseNo(e.getRoadTransportLicenseNo())
                .coldStorageCapacity(e.getColdStorageCapacity())
                .transportToolInfo(e.getTransportToolInfo())
                .displayEquipmentInfo(e.getDisplayEquipmentInfo())
                .status(e.getStatus())
                .statusName(statusName(e.getStatus()))
                .registerTime(e.getRegisterTime())
                .lastLoginTime(e.getLastLoginTime())
                .createTime(e.getCreateTime())
                .updateTime(e.getUpdateTime())
                .build();
    }

    /**
     * 本企业信息（接口 4.5 / 8.1）：按类型白名单返回资质字段。
     */
    public EnterpriseInfoVO toInfo(NodeEnterprise e, List<String> editableFields) {
        EnterpriseInfoVO.EnterpriseInfoVOBuilder builder = EnterpriseInfoVO.builder()
                .enterpriseId(e.getId())
                .enterpriseCode(e.getEnterpriseCode())
                .enterpriseName(e.getEnterpriseName())
                .enterpriseType(e.getEnterpriseType())
                .enterpriseTypeName(dictService.enterpriseTypeName(e.getEnterpriseType()))
                .loginName(e.getLoginName())
                .creditCode(e.getCreditCode())
                .legalPerson(e.getLegalPerson())
                .contactPerson(e.getContactPerson())
                .contactPhone(e.getContactPhone())
                .provinceCode(e.getProvinceCode())
                .provinceName(e.getProvinceName())
                .cityCode(e.getCityCode())
                .cityName(e.getCityName())
                .address(e.getAddress())
                .registerTime(e.getRegisterTime())
                .lastLoginTime(e.getLastLoginTime())
                .editableFields(editableFields);

        Integer type = e.getEnterpriseType();
        if (type != null) {
            switch (type) {
                case Constants.ENTERPRISE_TYPE_FISHING -> builder
                        .fisheryLicenseNo(e.getFisheryLicenseNo())
                        .aquacultureLicenseNo(e.getAquacultureLicenseNo())
                        .fryLicenseNo(e.getFryLicenseNo());
                case Constants.ENTERPRISE_TYPE_PROCESSING -> builder
                        .foodProductionLicenseNo(e.getFoodProductionLicenseNo())
                        .exportFilingNo(e.getExportFilingNo());
                case Constants.ENTERPRISE_TYPE_WHOLESALE -> builder
                        .foodBusinessLicenseNo(e.getFoodBusinessLicenseNo())
                        .roadTransportLicenseNo(e.getRoadTransportLicenseNo())
                        .coldStorageCapacity(e.getColdStorageCapacity())
                        .transportToolInfo(e.getTransportToolInfo());
                case Constants.ENTERPRISE_TYPE_RETAIL -> builder
                        .foodBusinessLicenseNo(e.getFoodBusinessLicenseNo())
                        .displayEquipmentInfo(e.getDisplayEquipmentInfo());
                default -> {
                    // 未知类型不返回任何资质字段
                }
            }
        }
        return builder.build();
    }

    private String statusName(Integer status) {
        if (status == null) {
            return null;
        }
        return status == Constants.STATUS_ENABLED ? "正常" : "停用";
    }
}
