import type { PageResult, PageQuery } from './api'

export type UserRole = 'admin' | 'enterprise'

export interface AdminLogin {
  token: string
  userId: string
  userType: string
  loginName: string
  adminName: string
  expiresIn: number
}

export interface EnterpriseLogin {
  token: string
  enterpriseId: string
  enterpriseCode: string
  enterpriseName: string
  enterpriseType: number
  enterpriseTypeName: string
  provinceName: string
  cityName: string
  expiresIn: number
}

export interface AdminInfo {
  userId?: string
  loginName?: string
  adminName?: string
  roles?: string[]
}

export interface EnterpriseInfo {
  enterpriseId: string
  enterpriseCode: string
  enterpriseName: string
  enterpriseType: number
  enterpriseTypeName: string
  loginName: string
  creditCode: string
  legalPerson?: string
  contactPerson?: string
  contactPhone?: string
  provinceCode: string
  provinceName: string
  cityCode: string
  cityName: string
  address?: string
  fisheryLicenseNo?: string
  aquacultureLicenseNo?: string
  fryLicenseNo?: string
  foodProductionLicenseNo?: string
  exportFilingNo?: string
  foodBusinessLicenseNo?: string
  roadTransportLicenseNo?: string
  coldStorageCapacity?: number
  transportToolInfo?: string
  displayEquipmentInfo?: string
  registerTime?: string
  lastLoginTime?: string
  editableFields?: string[]
}

export interface EnterpriseListItem {
  id: string
  enterpriseCode: string
  enterpriseName: string
  enterpriseType: number
  enterpriseTypeName: string
  creditCode: string
  legalPerson?: string
  contactPerson?: string
  contactPhone?: string
  provinceCode: string
  provinceName: string
  cityCode: string
  cityName: string
  address?: string
  status: number
  statusName: string
  registerTime?: string
}

export type EnterpriseDetail = EnterpriseInfo & {
  id: string
  status: number
  statusName: string
  createTime?: string
  updateTime?: string
}

export interface EnterpriseForm {
  enterpriseName: string
  loginName: string
  creditCode: string
  enterpriseType: number
  password?: string
  legalPerson?: string
  contactPerson?: string
  contactPhone?: string
  provinceCode: string
  cityCode: string
  address?: string
  status?: number
  fisheryLicenseNo?: string
  aquacultureLicenseNo?: string
  fryLicenseNo?: string
  foodProductionLicenseNo?: string
  exportFilingNo?: string
  foodBusinessLicenseNo?: string
  roadTransportLicenseNo?: string
  coldStorageCapacity?: number
  transportToolInfo?: string
  displayEquipmentInfo?: string
}

export interface Overview {
  totalCount: number
  fishingCount: number
  processingCount: number
  wholesaleCount: number
  retailCount: number
  provinceCount: number
  todayRegisterCount: number
  updateTime?: string
}

export interface RegisterTrend { months: string[]; counts: number[]; total: number }
export interface ProvinceDistribution { provinceCode: string; provinceName: string; count: number; percent: number }
export interface ProvinceCount { provinces: string[]; counts: number[] }
export interface TypeDistribution { enterpriseType: number; enterpriseTypeName: string; count: number; percent: number }

export type BatchStatus = 1 | 2 | 3 | 4 | 5

export interface BatchListItem {
  id: string
  batchNo: string
  productVariety?: string
  upstreamBatchNo?: string
  upstreamEnterpriseName?: string
  batchStatus: BatchStatus
  batchStatusName: string
  handoverTemp?: number
  coldChainOk?: boolean
  createTime?: string
}

export interface FishingInfo { catchBreedDate?: string; certificateType?: number; certificateTypeName?: string; certificateNo?: string; departureTemp?: number; drugReportNo?: string; fishingLogNo?: string }
export interface ProcessingInfo { processForm?: string; inspectionNo?: string; quickFreezeTemp?: number; factoryTemp?: number; productionBatchNo?: string }
export interface WholesaleInfo { wholesaleDate?: string; inboundTemp?: number; coldStorageTemp?: number; outboundTemp?: number; transportToolNo?: string; coldStorageNo?: string }
export interface RetailInfo { shelfDate?: string; displayTemp?: number; saleStore?: string; displayEquipNo?: string }
export interface TraceCodeInfo { traceCode: string; batchId?: string; batchNo?: string; productVariety?: string; saleStore?: string; qrContent?: string; status?: number; statusName?: string; generateTime?: string; queryCount?: number; lastQueryTime?: string }
export interface ConfirmBrief { requestNo: string; requestStatus: number; requestStatusName: string; requestTime?: string; handleTime?: string; handleRemark?: string }
export interface BatchDetail extends BatchListItem {
  enterpriseId?: string; enterpriseName?: string; enterpriseType?: number; enterpriseTypeName?: string; upstreamEnterpriseId?: string; upstreamEnterpriseName?: string; upstreamProvinceName?: string; upstreamCityName?: string; upstreamBatchId?: string; sourceType?: number; sourceTypeName?: string; publishTime?: string; offShelfTime?: string; remark?: string; createTime?: string; updateTime?: string; fishingInfo?: FishingInfo; processingInfo?: ProcessingInfo; wholesaleInfo?: WholesaleInfo; retailInfo?: RetailInfo; traceCode?: TraceCodeInfo; confirmRequest?: ConfirmBrief
}

export interface BatchForm {
  batchNo: string; productVariety?: string; sourceType?: number; publish?: boolean; sendConfirmRequest?: boolean; forceSubmit?: boolean; remark?: string; catchBreedDate?: string; certificateType?: number; certificateNo?: string; departureTemp?: number; drugReportNo?: string; fishingLogNo?: string; upstreamEnterpriseId?: string; upstreamBatchId?: string; processForm?: string; inspectionNo?: string; quickFreezeTemp?: number; factoryTemp?: number; productionBatchNo?: string; wholesaleDate?: string; inboundTemp?: number; coldStorageTemp?: number; outboundTemp?: number; transportToolNo?: string; coldStorageNo?: string; shelfDate?: string; displayTemp?: number; saleStore?: string; displayEquipNo?: string
}

export interface UpstreamEnterprise { id: string; enterpriseName: string; enterpriseCode?: string; enterpriseTypeName?: string; provinceName?: string; cityName?: string }
export interface UpstreamBatch { id: string; batchNo: string; productVariety?: string; enterpriseName?: string; enterpriseTypeName?: string; publishTime?: string }
export interface ConfirmRequest { id: string; requestNo: string; batchId: string; batchNo: string; upstreamBatchId?: string; upstreamBatchNo?: string; fromEnterpriseId?: string; fromEnterpriseName?: string; fromEnterpriseType?: number; fromEnterpriseTypeName?: string; toEnterpriseId?: string; toEnterpriseName?: string; handoverTemp?: number; requestStatus: number; requestStatusName: string; requestTime?: string }
export interface ConfirmRequestDetail extends ConfirmRequest { handleTime?: string; handleRemark?: string; downstreamBatch?: Record<string, unknown>; upstreamBatch?: Record<string, unknown> }

// 字段名以《前后端接口文档》12.1 的响应示例为准：曲线点用 recordTime 携带采样时间，并带合格阈值 threshold。
export interface TemperaturePoint { stageCode?: number; stageName?: string; enterpriseName?: string; temperature?: number; threshold?: number; recordTime?: string; qualified?: boolean }
// 环节内的温度明细（links[].temperatures）与曲线点结构不同：这里是「记录项名称 + 数值」。
export interface TemperatureReading { name?: string; value?: number; threshold?: number; qualified?: boolean }
export interface Certificate { name?: string; no?: string }
export interface TraceLink { stageCode: number; stageName: string; enterpriseName: string; enterpriseTypeName?: string; provinceName?: string; cityName?: string; batchNo?: string; upstreamBatchNo?: string; productVariety?: string; sourceTypeName?: string; handoverTemp?: number; coldChainOk?: boolean; handoverTime?: string; certificates?: Certificate[]; temperatures?: TemperatureReading[] }
export interface PublicTrace { traceCode: string; batchNo: string; productVariety?: string; retailerName?: string; saleStore?: string; generateTime?: string; queryCount?: number; coldChainQualified?: boolean; coldChainConclusion?: string; temperatureCurve?: TemperaturePoint[]; links: TraceLink[] }

export type Paged<T> = PageResult<T>
export type WithPage<T> = PageQuery & T
