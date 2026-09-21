package com.fsts.trace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 节点企业新增 / 更新的公共字段（接口 6.3 / 6.4）。
 *
 * <p>资质字段按企业类型选择性必填，由 {@code EnterpriseValidator} 在服务层统一校验，
 * 因此这里不加必填注解，避免"与类型无关的字段被误判为必填"。
 */
@Data
public class EnterpriseSaveRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "企业名称不能为空")
    @Size(max = 100, message = "企业名称长度不能超过 100 个字符")
    private String enterpriseName;

    @NotBlank(message = "登录账号不能为空")
    @Size(max = 50, message = "登录账号长度不能超过 50 个字符")
    private String loginName;

    @NotBlank(message = "统一社会信用代码不能为空")
    @Size(max = 50, message = "统一社会信用代码长度不能超过 50 个字符")
    private String creditCode;

    @Size(max = 50, message = "法定代表人长度不能超过 50 个字符")
    private String legalPerson;

    @Size(max = 50, message = "联系人长度不能超过 50 个字符")
    private String contactPerson;

    @Size(max = 20, message = "联系电话长度不能超过 20 个字符")
    private String contactPhone;

    @NotBlank(message = "所在省不能为空")
    private String provinceCode;

    @NotBlank(message = "所在市不能为空")
    private String cityCode;

    @Size(max = 200, message = "详细地址长度不能超过 200 个字符")
    private String address;

    private Integer status;

    // ---- 类型 1：捕捞与养殖企业 ----
    @Size(max = 60, message = "渔业捕捞许可证编号过长")
    private String fisheryLicenseNo;

    @Size(max = 60, message = "水域滩涂养殖证编号过长")
    private String aquacultureLicenseNo;

    @Size(max = 60, message = "水产苗种生产许可证编号过长")
    private String fryLicenseNo;

    // ---- 类型 2：冷冻加工企业 ----
    @Size(max = 60, message = "食品生产许可证编号过长")
    private String foodProductionLicenseNo;

    @Size(max = 60, message = "出口食品生产企业备案编号过长")
    private String exportFilingNo;

    // ---- 类型 3 / 4 ----
    @Size(max = 60, message = "食品经营许可证编号过长")
    private String foodBusinessLicenseNo;

    @Size(max = 60, message = "道路运输经营许可证编号过长")
    private String roadTransportLicenseNo;

    // ---- 类型 3：批发商 ----
    private BigDecimal coldStorageCapacity;

    @Size(max = 200, message = "冷藏运输工具信息过长")
    private String transportToolInfo;

    // ---- 类型 4：零售商 ----
    @Size(max = 200, message = "冷冻陈列设备信息过长")
    private String displayEquipmentInfo;
}
