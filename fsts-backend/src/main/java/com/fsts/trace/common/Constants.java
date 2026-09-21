package com.fsts.trace.common;

/**
 * 全局业务常量与枚举取值（与《前后端接口文档》第 3 章一致）。
 */
public final class Constants {

    private Constants() {
    }

    /** 企业类型：捕捞与养殖企业（链条源头，无上游、无确认请求） */
    public static final int ENTERPRISE_TYPE_FISHING = 1;
    /** 企业类型：冷冻加工企业 */
    public static final int ENTERPRISE_TYPE_PROCESSING = 2;
    /** 企业类型：批发商 */
    public static final int ENTERPRISE_TYPE_WHOLESALE = 3;
    /** 企业类型：零售商（链末端，确认后生成溯源标识码） */
    public static final int ENTERPRISE_TYPE_RETAIL = 4;

    /** 批号状态：新建 / 待发布 */
    public static final int BATCH_STATUS_NEW = 1;
    /** 批号状态：待确认 */
    public static final int BATCH_STATUS_PENDING = 2;
    /** 批号状态：已确认 / 已发布 */
    public static final int BATCH_STATUS_CONFIRMED = 3;
    /** 批号状态：已下架 */
    public static final int BATCH_STATUS_OFF_SHELF = 4;

    /** 确认请求状态：待确认 */
    public static final int REQUEST_STATUS_PENDING = 1;
    /** 确认请求状态：已确认 */
    public static final int REQUEST_STATUS_CONFIRMED = 2;
    /** 确认请求状态：已拒绝 */
    public static final int REQUEST_STATUS_REJECTED = 3;
    /** 确认请求状态：已撤回 */
    public static final int REQUEST_STATUS_WITHDRAWN = 4;

    /** 企业状态：正常 */
    public static final int STATUS_ENABLED = 1;
    /** 企业状态：停用 */
    public static final int STATUS_DISABLED = 0;

    /** 用户类型：系统管理员 */
    public static final String USER_TYPE_ADMIN = "ADMIN";
    /** 用户类型：节点企业 */
    public static final String USER_TYPE_ENTERPRISE = "ENTERPRISE";

    /** 冷链常规阈值（℃）：不高于该值判定合格 */
    public static final java.math.BigDecimal TEMP_THRESHOLD_NORMAL = new java.math.BigDecimal("-18.00");
    /** 速冻中心温度阈值（℃） */
    public static final java.math.BigDecimal TEMP_THRESHOLD_QUICK_FREEZE = new java.math.BigDecimal("-35.00");

    /** 企业编码前缀 */
    public static final String ENTERPRISE_CODE_PREFIX = "FSTS-E-";
    /** 确认请求单号前缀 */
    public static final String REQUEST_NO_PREFIX = "FSTS-CR-";
    /** 溯源码前缀 */
    public static final String TRACE_CODE_PREFIX = "FSTS-";

    /** 默认初始密码（管理端新建企业未指定密码时使用） */
    public static final String DEFAULT_PASSWORD = "123456";
}
