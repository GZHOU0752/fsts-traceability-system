package com.fsts.trace.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 批量导入的单行数据（接口 10.12）。
 *
 * <p>继承 {@link BatchSaveRequest}，因此单条新建的字段规则（必填、长度、字典取值）
 * 与逐条录入完全一致——导入走的是同一个 {@code create} 事务，不会出现
 * "界面能建、导入能建出脏数据"的两套标准。
 *
 * <p>额外支持用<b>批号</b>而不是 ID 指定上游：批量导入的场景下用户手里只有
 * 上游批号（来自对方的单据），不可能去查数据库主键，因此这里允许填
 * {@code upstreamBatchNo}，由服务端在本企业的合法上游范围内解析成 ID。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BatchImportRow extends BatchSaveRequest {

    /** 行号（从 1 开始，用于回显失败原因） */
    private Integer rowNo;

    /** 上游产品批号（类型 2/3/4 必填，服务端按批号解析上游企业与批号 ID） */
    private String upstreamBatchNo;

    /** 上游企业名称（可选）：同一批号在本企业上游范围内不唯一时用于消歧 */
    private String upstreamEnterpriseName;
}
