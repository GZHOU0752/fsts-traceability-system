import { request } from '@/lib/http'
import { toPageParams } from './common'
import type { PageResult } from '@/types/api'
import type { EnterpriseDetail, EnterpriseForm, EnterpriseListItem, Overview, ProvinceCount, ProvinceDistribution, RegisterTrend, TypeDistribution } from '@/types/domain'

export interface EnterpriseQuery { current?: number; size?: number; id?: string; enterpriseName?: string; creditCode?: string; provinceCode?: string; cityCode?: string; enterpriseType?: number; status?: number }

export const adminApi = {
  enterprises: (query: EnterpriseQuery) => request<PageResult<EnterpriseListItem>>({ method: 'GET', url: '/admin/enterprises', params: toPageParams(query as EnterpriseQuery & Record<string, unknown>) }),
  enterpriseDetail: (id: string) => request<EnterpriseDetail>({ method: 'GET', url: `/admin/enterprises/${id}` }),
  createEnterprise: (payload: EnterpriseForm) => request<{ id: string; enterpriseCode: string; enterpriseName: string }>({ method: 'POST', url: '/admin/enterprises', data: payload }),
  updateEnterprise: (id: string, payload: EnterpriseForm) => request<void>({ method: 'PUT', url: `/admin/enterprises/${id}`, data: payload }),
  deleteEnterprise: (id: string) => request<void>({ method: 'DELETE', url: `/admin/enterprises/${id}` }),
  checkEnterprise: (field: string, value: string, excludeId?: string) => request<{ available: boolean; message?: string }>({ method: 'GET', url: '/admin/enterprises/check-name', params: { field, value, excludeId } }),
  overview: () => request<Overview>({ method: 'GET', url: '/admin/statistics/overview' }),
  registerTrend: (months = 12) => request<RegisterTrend>({ method: 'GET', url: '/admin/statistics/register-trend', params: { months } }),
  provinceDistribution: (top = 10) => request<ProvinceDistribution[]>({ method: 'GET', url: '/admin/statistics/province-distribution', params: { top } }),
  provinceCount: () => request<ProvinceCount>({ method: 'GET', url: '/admin/statistics/province-count' }),
  typeDistribution: () => request<TypeDistribution[]>({ method: 'GET', url: '/admin/statistics/type-distribution' }),
}
