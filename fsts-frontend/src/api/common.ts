import { request } from '@/lib/http'
import type { DictItem, Region, PageQuery } from '@/types/api'

export const commonApi = {
  provinces: () => request<Region[]>({ method: 'GET', url: '/common/regions/provinces' }),
  cities: (provinceCode: string) => request<Region[]>({ method: 'GET', url: '/common/regions/cities', params: { provinceCode } }),
  dicts: (typeCode: string) => request<DictItem[]>({ method: 'GET', url: `/common/dicts/${typeCode}` }),
}

export const toPageParams = (query: PageQuery & Record<string, unknown>) => ({ current: query.current ?? 1, size: query.size ?? 10, ...query })
