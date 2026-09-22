export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface PageQuery {
  current?: number
  size?: number
}

export interface Region {
  regionCode: string
  regionName: string
  shortName?: string
  sortNo?: number
}

export interface DictItem {
  itemCode: string
  itemValue: string
  sortNo?: number
}
