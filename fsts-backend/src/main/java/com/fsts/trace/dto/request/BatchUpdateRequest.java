package com.fsts.trace.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新产品批号请求体（接口 10.4）。
 *
 * <p>与新建的差异：
 * <ul>
 *   <li>{@code batchNo} 不可修改——服务层收到后忽略而不是报错；</li>
 *   <li>{@code upstreamEnterpriseId} / {@code upstreamBatchId} 允许更换，更换后上游带出字段整体刷新。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BatchUpdateRequest extends BatchSaveRequest {
}
