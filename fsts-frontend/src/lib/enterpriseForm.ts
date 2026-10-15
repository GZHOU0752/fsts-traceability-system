import type { EnterpriseDetail, EnterpriseForm } from '@/types/domain'

/**
 * 管理端企业表单的字段模型。
 *
 * <p>存在的理由：接口文档 6.3 规定"资质字段随企业类型变化，且必填规则由类型决定"
 * （类型 1 捕捞证/养殖证二选一、类型 2 生产许可证、类型 3/4 经营许可证）。
 * 后端 EnterpriseValidator 会强制校验这些字段，前端若只放公共字段，
 * 新建企业必然返回 400。把规则集中到这里，页面只负责渲染，避免两边各写一遍后漂移。
 */

export type LicenseFieldType = 'text' | 'number'

export interface LicenseField {
  /** 提交到后端的字段名 */
  key: keyof EnterpriseForm
  label: string
  /** 单独必填；类型 1 的两个证件用"至少一项"规则，因此都是 false */
  required: boolean
  type?: LicenseFieldType
  placeholder?: string
  hint?: string
  /** 是否独占整行 */
  wide?: boolean
}

/** 类型 1：捕捞证与养殖证至少一项，因此二者都标记为"非单独必填"。 */
export const LICENSE_FIELDS_BY_TYPE: Record<number, LicenseField[]> = {
  1: [
    { key: 'fisheryLicenseNo', label: '渔业捕捞许可证编号', required: false, placeholder: 'LYQZ-2025-3702-0071', hint: '海洋捕捞必填' },
    { key: 'aquacultureLicenseNo', label: '水域滩涂养殖证编号', required: false, placeholder: 'YZQZ-2026-2102-0033', hint: '水产养殖必填' },
    { key: 'fryLicenseNo', label: '水产苗种生产许可证编号', required: false, placeholder: '苗种繁育企业填写' },
  ],
  2: [
    { key: 'foodProductionLicenseNo', label: '食品生产许可证编号', required: true, placeholder: 'SC11337060200136' },
    { key: 'exportFilingNo', label: '出口食品生产企业备案编号', required: false, placeholder: '出口产品填写' },
  ],
  3: [
    { key: 'foodBusinessLicenseNo', label: '食品经营许可证编号', required: true, placeholder: 'JY13101150028641' },
    { key: 'roadTransportLicenseNo', label: '道路运输经营许可证编号', required: false, placeholder: '自营冷藏运输填写' },
    { key: 'coldStorageCapacity', label: '冷库容量（吨）', required: false, type: 'number', placeholder: '例如 3200' },
    { key: 'transportToolInfo', label: '冷藏运输工具信息', required: false, wide: true, placeholder: '例如 冷藏车 6 辆（沪B-LC0231 等）' },
  ],
  4: [
    { key: 'foodBusinessLicenseNo', label: '食品经营许可证编号', required: true, placeholder: 'JY13101150031972' },
    { key: 'displayEquipmentInfo', label: '冷冻陈列设备信息', required: false, wide: true, placeholder: '例如 冷冻岛柜 8 台（LG-01 至 LG-08，单体温度 -20 ℃）' },
  ],
}

/** 公共字段（所有类型都要填） */
export const COMMON_FIELDS: LicenseField[] = [
  { key: 'enterpriseName', label: '企业名称', required: true, placeholder: '请输入企业全称' },
  { key: 'loginName', label: '登录账号', required: true, placeholder: '用于企业端登录' },
  { key: 'creditCode', label: '统一社会信用代码', required: true, placeholder: '请输入统一社会信用代码' },
  { key: 'legalPerson', label: '法定代表人', required: false },
  { key: 'contactPerson', label: '联系人', required: false },
  { key: 'contactPhone', label: '联系电话', required: false },
]

export function licenseFieldsOf(enterpriseType?: number): LicenseField[] {
  if (!enterpriseType) return []
  return LICENSE_FIELDS_BY_TYPE[enterpriseType] ?? []
}

/** 全部资质字段名（用于切换企业类型时清空，避免残留上个类型填过的值） */
export const ALL_LICENSE_KEYS: Array<keyof EnterpriseForm> = [
  'fisheryLicenseNo',
  'aquacultureLicenseNo',
  'fryLicenseNo',
  'foodProductionLicenseNo',
  'exportFilingNo',
  'foodBusinessLicenseNo',
  'roadTransportLicenseNo',
  'coldStorageCapacity',
  'transportToolInfo',
  'displayEquipmentInfo',
]

/** 清空所有资质字段（切换企业类型时调用） */
export function resetLicenseFields(form: EnterpriseForm): void {
  for (const key of ALL_LICENSE_KEYS) {
    ;(form as unknown as Record<string, unknown>)[key] = undefined
  }
}

/** 新建时的空白表单：字段必须与后端 EnterpriseSaveRequest 对齐，漏字段就会 400 */
export function createEmptyEnterpriseForm(): EnterpriseForm {
  return {
    enterpriseName: '',
    loginName: '',
    creditCode: '',
    enterpriseType: 1,
    password: '',
    legalPerson: '',
    contactPerson: '',
    contactPhone: '',
    provinceCode: '',
    cityCode: '',
    address: '',
    status: 1,
    fisheryLicenseNo: '',
    aquacultureLicenseNo: '',
    fryLicenseNo: '',
    foodProductionLicenseNo: '',
    exportFilingNo: '',
    foodBusinessLicenseNo: '',
    roadTransportLicenseNo: '',
    coldStorageCapacity: undefined,
    transportToolInfo: '',
    displayEquipmentInfo: '',
  }
}

/**
 * 用详情回显表单：只保留本类型的资质字段，避免把上个类型填过的值带过去
 * （后端虽然会忽略无关资质，但前端界面会残留脏值，给用户造成困惑）。
 */
export function toEnterpriseForm(detail: EnterpriseDetail): EnterpriseForm {
  const form = createEmptyEnterpriseForm()
  const keep = new Set<string>([
    ...COMMON_FIELDS.map((f) => String(f.key)),
    ...licenseFieldsOf(detail.enterpriseType).map((f) => String(f.key)),
    // 省市在模板里是独立的级联下拉，不在 COMMON_FIELDS 里，忘了带上就会出现
    // "编辑对话框打开后省市为空、保存被前端校验拦住"（e2e 已锁定该回归）
    'provinceCode',
    'cityCode',
    'address',
    'status',
  ])
  for (const [key, value] of Object.entries(detail)) {
    if (keep.has(key) && value !== undefined) {
      ;(form as unknown as Record<string, unknown>)[key] = value
    }
  }
  form.enterpriseType = detail.enterpriseType
  return form
}

/**
 * 提交前的前端校验：与后端 EnterpriseValidator 规则一致，命中时返回提示文案。
 * 目的不是替代后端校验，而是把"必然失败的请求"挡在本地，给出可操作的提示。
 */
export function validateEnterpriseForm(form: EnterpriseForm): string | null {
  for (const field of COMMON_FIELDS) {
    if (!field.required) continue
    const value = form[field.key]
    if (value === undefined || value === null || String(value).trim() === '') {
      return `请填写${field.label}`
    }
  }
  if (!form.provinceCode) return '请选择省份'
  if (!form.cityCode) return '请选择城市'

  for (const field of licenseFieldsOf(form.enterpriseType)) {
    if (!field.required) continue
    const value = form[field.key]
    if (value === undefined || value === null || String(value).trim() === '') {
      return `请填写${field.label}`
    }
  }

  if (form.enterpriseType === 1 && !hasText(form.fisheryLicenseNo) && !hasText(form.aquacultureLicenseNo)) {
    return '捕捞与养殖企业须至少填写渔业捕捞许可证编号或水域滩涂养殖证编号'
  }
  return null
}

function hasText(value?: string | null): boolean {
  return value !== undefined && value !== null && String(value).trim() !== ''
}

/**
 * 表单字段 → 提交报文。
 *
 * <p>关键点：<b>空密码必须整个字段删掉</b>。后端 `password` 上有
 * `@Size(min = 6, max = 20)`，传空字符串会被 Bean Validation 直接判 400
 * （而不是走"不传则使用默认密码 123456"的分支），
 * 表现为"填完所有必填项点保存却没有任何明显反馈"。
 */
export function toEnterprisePayload(form: EnterpriseForm, editing: boolean): EnterpriseForm {
  const payload: EnterpriseForm = { ...form }
  if (editing || !payload.password?.trim()) {
    delete payload.password
  }
  return payload
}
