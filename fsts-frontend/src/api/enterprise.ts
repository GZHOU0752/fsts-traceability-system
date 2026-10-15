import { request } from '@/lib/http'
import { toPageParams } from './common'
import type { PageResult } from '@/types/api'
import type { BatchDetail, BatchForm, BatchImportResult, BatchListItem, BatchPublishResult, ConfirmRequest, ConfirmRequestDetail, EnterpriseInfo, TraceCodeInfo, UpstreamBatch, UpstreamEnterprise, WithdrawResult } from '@/types/domain'

export interface BatchQuery { current?: number; size?: number; batchStatus: number; batchNo?: string; productVariety?: string; upstreamBatchNo?: string }
export interface ConfirmQuery { current?: number; size?: number; requestStatus?: number; downstreamEnterpriseName?: string; batchNo?: string }

export const enterpriseApi = {
  profile: () => request<EnterpriseInfo>({ method: 'GET', url: '/enterprise/profile' }),
  upstreamEnterprises: (query: Record<string, unknown>) => request<PageResult<UpstreamEnterprise>>({ method: 'GET', url: '/enterprise/upstream/enterprises', params: toPageParams(query as Record<string, unknown> & { current?: number; size?: number }) }),
  upstreamBatches: (query: Record<string, unknown>) => request<PageResult<UpstreamBatch>>({ method: 'GET', url: '/enterprise/upstream/batches', params: toPageParams(query as Record<string, unknown> & { current?: number; size?: number }) }),
  batches: (query: BatchQuery) => request<PageResult<BatchListItem>>({ method: 'GET', url: '/enterprise/batches', params: toPageParams(query as BatchQuery & Record<string, unknown>) }),
  batchDetail: (id: string) => request<BatchDetail>({ method: 'GET', url: `/enterprise/batches/${id}` }),
  createBatch: (payload: BatchForm) => request<BatchDetail>({ method: 'POST', url: '/enterprise/batches', data: payload }),
  updateBatch: (id: string, payload: BatchForm) => request<BatchDetail>({ method: 'PUT', url: `/enterprise/batches/${id}`, data: payload }),
  deleteBatch: (id: string) => request<void>({ method: 'DELETE', url: `/enterprise/batches/${id}` }),
  publishBatch: (id: string) => request<BatchPublishResult>({ method: 'PUT', url: `/enterprise/batches/${id}/publish` }),
  offShelfBatch: (id: string, remark?: string) => request<BatchPublishResult>({ method: 'PUT', url: `/enterprise/batches/${id}/off-shelf`, data: remark ? { remark } : undefined }),
  /** 恢复上架（接口 10.11）：下架后 60 秒内的撤销、以及详情页的恢复入口 */
  restoreBatch: (id: string) => request<BatchPublishResult>({ method: 'PUT', url: `/enterprise/batches/${id}/restore` }),
  /** 批量导入（接口 10.12）：前端解析 CSV 后按行提交 */
  importBatches: (rows: BatchForm[]) => request<BatchImportResult>({ method: 'POST', url: '/enterprise/batches/import', data: { rows } }),
  checkBatchNo: (batchNo: string, excludeId?: string) => request<{ available: boolean; message?: string }>({ method: 'GET', url: '/enterprise/batches/check-batch-no', params: { batchNo, excludeId } }),
  sendConfirmRequest: (id: string) => request<{ requestNo: string; requestStatusName: string }>({ method: 'POST', url: `/enterprise/batches/${id}/confirm-request` }),
  traceCode: (id: string) => request<TraceCodeInfo>({ method: 'GET', url: `/enterprise/batches/${id}/trace-code` }),
  confirmRequests: (query: ConfirmQuery) => request<PageResult<ConfirmRequest>>({ method: 'GET', url: '/enterprise/confirm-requests', params: toPageParams(query as ConfirmQuery & Record<string, unknown>) }),
  confirmRequestDetail: (id: string) => request<ConfirmRequestDetail>({ method: 'GET', url: `/enterprise/confirm-requests/${id}` }),
  confirmRequest: (id: string, handleRemark?: string) => request<ConfirmRequestDetail>({ method: 'PUT', url: `/enterprise/confirm-requests/${id}/confirm`, data: handleRemark ? { handleRemark } : undefined }),
  rejectRequest: (id: string, handleRemark: string) => request<ConfirmRequestDetail>({ method: 'PUT', url: `/enterprise/confirm-requests/${id}/reject`, data: { handleRemark } }),
  /** 撤回自己发起的、尚未被上游处理的确认请求（接口 11.5） */
  withdrawRequest: (id: string, handleRemark?: string) => request<WithdrawResult>({ method: 'PUT', url: `/enterprise/confirm-requests/${id}/withdraw`, data: handleRemark ? { handleRemark } : undefined }),
}
