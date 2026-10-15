import type { BatchDetail, BatchForm } from '@/types/domain'

/**
 * 企业端批号表单的字段模型（接口文档 10.3 / 附录 A.2 "按企业类型渲染表单"）。
 *
 * <p>四个环节的必填项完全不同，后端由 BatchValidator 策略强制校验：
 * 类型 1 要品种、来源、捕捞日期、证明类型、证明编号、起运温度；
 * 类型 2/3/4 要上游批号 + 本环节明细字段。前端表单必须同构，
 * 否则用户填完只会收到 400。
 */

export type BatchFieldKind = 'text' | 'number' | 'date' | 'select' | 'dict'

export interface BatchField {
  key: keyof BatchForm
  label: string
  kind: BatchFieldKind
  required: boolean
  /** kind = dict 时使用的字典 typeCode */
  dictType?: string
  /**
   * kind = dict 时提交哪一列：
   * - code：提交 itemCode 的数字值（如证明类型 1/2）
   * - value：提交 itemValue 的文本值（如加工形态"鱼片"，业务库按中文名称存储）
   */
  dictValue?: 'code' | 'value'
  /** kind = select 时的固定选项 */
  options?: Array<{ label: string; value: number }>
  unit?: string
  placeholder?: string
  wide?: boolean
}

const SOURCE_TYPE_OPTIONS = [
  { label: '养殖', value: 1 },
  { label: '捕捞', value: 2 },
]

/** 类型 1：源头环节，没有上游 */
const FISHING_FIELDS: BatchField[] = [
  { key: 'productVariety', label: '产品品种', kind: 'text', required: true, placeholder: '例如：阿根廷红虾' },
  { key: 'sourceType', label: '来源类型', kind: 'select', required: true, options: SOURCE_TYPE_OPTIONS },
  { key: 'catchBreedDate', label: '捕捞 / 养殖日期', kind: 'date', required: true },
  { key: 'certificateType', label: '证明类型', kind: 'dict', dictType: 'certificate_type', required: true },
  { key: 'certificateNo', label: '证明编号', kind: 'text', required: true, placeholder: '产地检疫合格证明 / 渔获物上岸证明编号' },
  { key: 'departureTemp', label: '起运温度', kind: 'number', required: true, unit: '℃', placeholder: '-18.0' },
  { key: 'drugReportNo', label: '药物残留检测报告编号', kind: 'text', required: false },
  { key: 'fishingLogNo', label: '渔捞日志编号', kind: 'text', required: false },
]

/** 类型 2：冷冻加工 */
const PROCESSING_FIELDS: BatchField[] = [
  { key: 'processForm', label: '加工形态', kind: 'dict', dictType: 'process_form', dictValue: 'value', required: true },
  { key: 'inspectionNo', label: '出厂检验报告编号', kind: 'text', required: true },
  { key: 'quickFreezeTemp', label: '速冻中心温度', kind: 'number', required: true, unit: '℃', placeholder: '-35.0' },
  { key: 'factoryTemp', label: '出厂温度', kind: 'number', required: true, unit: '℃', placeholder: '-18.0' },
  { key: 'productionBatchNo', label: '生产批次记录编号', kind: 'text', required: false },
]

/** 类型 3：批发与冷链仓储 */
const WHOLESALE_FIELDS: BatchField[] = [
  { key: 'wholesaleDate', label: '批发日期', kind: 'date', required: true },
  { key: 'inboundTemp', label: '入库温度', kind: 'number', required: true, unit: '℃' },
  { key: 'coldStorageTemp', label: '冷库温度', kind: 'number', required: true, unit: '℃' },
  { key: 'outboundTemp', label: '出库温度', kind: 'number', required: true, unit: '℃' },
  { key: 'transportToolNo', label: '运输工具编号', kind: 'text', required: true },
  { key: 'coldStorageNo', label: '冷库编号', kind: 'text', required: false },
]

/** 类型 4：零售终端 */
const RETAIL_FIELDS: BatchField[] = [
  { key: 'shelfDate', label: '上架日期', kind: 'date', required: true },
  { key: 'displayTemp', label: '冷冻陈列柜温度', kind: 'number', required: true, unit: '℃' },
  { key: 'saleStore', label: '销售门店', kind: 'text', required: true },
  { key: 'displayEquipNo', label: '陈列设备编号', kind: 'text', required: false },
]

const FIELDS_BY_TYPE: Record<number, BatchField[]> = {
  1: FISHING_FIELDS,
  2: PROCESSING_FIELDS,
  3: WHOLESALE_FIELDS,
  4: RETAIL_FIELDS,
}

export function batchFieldsOf(enterpriseType?: number): BatchField[] {
  if (!enterpriseType) return []
  return FIELDS_BY_TYPE[enterpriseType] ?? []
}

/** 类型 2/3/4 必须选择上游企业与上游批号（后端 BatchValidator 强制校验） */
export function requiresUpstream(enterpriseType?: number): boolean {
  return enterpriseType === 2 || enterpriseType === 3 || enterpriseType === 4
}

/** 类型 1 保存后可直接发布，其余类型只能通过上游确认进入"已确认" */
export function canPublishDirectly(enterpriseType?: number): boolean {
  return enterpriseType === 1
}

/** 链条源头没有上游企业 */
export function hasUpstream(enterpriseType?: number): boolean {
  return requiresUpstream(enterpriseType)
}

/** 只有零售商在确认后生成溯源标识码 */
export function hasTraceCode(enterpriseType?: number): boolean {
  return enterpriseType === 4
}

export function createEmptyBatchForm(enterpriseType: number): BatchForm {
  return {
    batchNo: '',
    productVariety: '',
    sourceType: enterpriseType === 1 ? 1 : undefined,
    publish: false,
    sendConfirmRequest: false,
    forceSubmit: false,
    remark: '',
    upstreamEnterpriseId: '',
    upstreamBatchId: '',
    ...(enterpriseType === 1
      ? {
          catchBreedDate: '',
          certificateType: undefined,
          certificateNo: '',
          departureTemp: undefined,
          drugReportNo: '',
          fishingLogNo: '',
        }
      : {}),
    ...(enterpriseType === 2
      ? {
          processForm: '',
          inspectionNo: '',
          quickFreezeTemp: undefined,
          factoryTemp: undefined,
          productionBatchNo: '',
        }
      : {}),
    ...(enterpriseType === 3
      ? {
          wholesaleDate: '',
          inboundTemp: undefined,
          coldStorageTemp: undefined,
          outboundTemp: undefined,
          transportToolNo: '',
          coldStorageNo: '',
        }
      : {}),
    ...(enterpriseType === 4
      ? {
          shelfDate: '',
          displayTemp: undefined,
          saleStore: '',
          displayEquipNo: '',
        }
      : {}),
  }
}

/**
 * 编辑回显：批号主表字段 + 本类型明细字段。
 * 后端详情把明细放在 fishingInfo / processingInfo / wholesaleInfo / retailInfo 里，
 * 这里按类型拍平成表单字段。
 */
export function toBatchForm(detail: BatchDetail, enterpriseType: number): BatchForm {
  const form = createEmptyBatchForm(enterpriseType)
  form.batchNo = detail.batchNo
  form.productVariety = detail.productVariety ?? ''
  form.sourceType = detail.sourceType ?? form.sourceType
  form.remark = detail.remark ?? ''
  form.upstreamEnterpriseId = detail.upstreamEnterpriseId ?? ''
  form.upstreamBatchId = detail.upstreamBatchId ?? ''

  Object.assign(form, stageInfoOf(detail, enterpriseType))
  return form
}

/**
 * 取批号详情里属于"当前环节"的明细字段。
 * 后端把明细按环节放在 fishingInfo / processingInfo / wholesaleInfo / retailInfo 四个对象里，
 * 这里统一拍平，供表单回显与详情抽屉展示复用。
 */
export function stageInfoOf(detail: BatchDetail, enterpriseType: number): Record<string, unknown> {
  const byType: Record<number, object | undefined> = {
    1: detail.fishingInfo,
    2: detail.processingInfo,
    3: detail.wholesaleInfo,
    4: detail.retailInfo,
  }
  return { ...(byType[enterpriseType] ?? {}) } as Record<string, unknown>
}

/** 提交前校验：返回第一条错误提示；上游选择单独由页面控制 */
export function validateBatchForm(enterpriseType: number, form: BatchForm): string | null {
  if (!hasText(form.batchNo)) return '请填写产品批号'

  if (requiresUpstream(enterpriseType)) {
    if (!form.upstreamEnterpriseId) return '请选择上游企业'
    if (!form.upstreamBatchId) return '请选择上游产品批号'
  }
  for (const field of batchFieldsOf(enterpriseType)) {
    if (!field.required) continue
    const value = form[field.key]
    if (value === undefined || value === null || String(value).trim() === '') {
      return `请填写${field.label}`
    }
  }
  return null
}

function hasText(value?: string | number | null): boolean {
  return value !== undefined && value !== null && String(value).trim() !== ''
}

// ======================================================================
// 批量导入（接口 10.12）
// ======================================================================

export interface ImportColumn {
  /** 提交字段名 */
  key: string
  /** 中文列名（模板表头用它，用户也可以在表头里写字段名） */
  label: string
  required: boolean
  /** 布尔列：接受 是/否、true/false、1/0 */
  boolean?: boolean
}

/**
 * 导入模板的列定义。
 *
 * <p>与逐条录入共用同一批字段（见 {@link batchFieldsOf}），差别只在于下游环节
 * 用「上游批号」而不是上游主键来指定来源——用户手里的单据上只有批号。
 */
export function importColumnsOf(enterpriseType?: number): ImportColumn[] {
  const columns: ImportColumn[] = [{ key: 'batchNo', label: '产品批号', required: true }]
  if (enterpriseType === 1) {
    columns.push(
      { key: 'productVariety', label: '产品品种', required: true },
      { key: 'sourceType', label: '来源类型（1 养殖 / 2 捕捞）', required: true },
      { key: 'catchBreedDate', label: '捕捞或养殖日期（yyyy-MM-dd）', required: true },
      { key: 'certificateType', label: '证明类型（1 产地检疫 / 2 渔获物上岸）', required: true },
      { key: 'certificateNo', label: '证明编号', required: true },
      { key: 'departureTemp', label: '起运温度（℃）', required: true },
      { key: 'drugReportNo', label: '药物残留检测报告编号', required: false },
      { key: 'fishingLogNo', label: '渔捞日志编号', required: false },
      { key: 'publish', label: '导入后直接发布（是/否）', required: false, boolean: true },
    )
  } else {
    columns.push(
      { key: 'upstreamBatchNo', label: '上游产品批号', required: true },
      { key: 'upstreamEnterpriseName', label: '上游企业名称（同名批号时填写）', required: false },
    )
    for (const field of batchFieldsOf(enterpriseType)) {
      columns.push({ key: String(field.key), label: field.label, required: field.required })
    }
    columns.push({ key: 'sendConfirmRequest', label: '导入后发送确认请求（是/否）', required: false, boolean: true })
  }
  columns.push({ key: 'remark', label: '备注', required: false })
  return columns
}

export interface ImportRowPreview {
  rowNo: number
  batchNo?: string
  form: BatchForm
  /** 解析或校验失败的原因，null 表示可以提交 */
  error: string | null
}

/**
 * 把 CSV 矩阵解析成可提交的行。
 *
 * <p>表头既接受中文列名（模板导出的样子），也接受字段名（`batchNo`），
 * 因为用户很可能拿系统里已有的导出文件来改。
 */
export function parseImportMatrix(enterpriseType: number, matrix: string[][]): ImportRowPreview[] {
  if (matrix.length < 2) {
    return []
  }
  const columns = importColumnsOf(enterpriseType)
  const header = matrix[0].map((cell) => String(cell ?? '').trim())
  const indexByField = new Map<string, number>()
  header.forEach((title, index) => {
    const column = columns.find((item) => item.label === title || item.key === title)
    if (column) {
      indexByField.set(column.key, index)
    }
  })

  const missing = columns.filter((item) => item.required && !indexByField.has(item.key))
  const result: ImportRowPreview[] = []
  for (let rowIndex = 1; rowIndex < matrix.length; rowIndex += 1) {
    const cells = matrix[rowIndex]
    const form = createEmptyBatchForm(enterpriseType)
    const record = form as unknown as Record<string, unknown>
    for (const column of columns) {
      const index = indexByField.get(column.key)
      if (index === undefined) continue
      const raw = String(cells[index] ?? '').trim()
      if (raw === '') continue
      record[column.key] = column.boolean ? parseBoolean(raw) : parseValue(enterpriseType, column.key, raw)
    }
    const batchNo = String(form.batchNo ?? '').trim()
    const upstreamCell = indexByField.get('upstreamBatchNo')
    const upstreamBatchNo = upstreamCell === undefined ? '' : String(cells[upstreamCell] ?? '').trim()
    let error: string | null = null
    if (missing.length) {
      error = `模板缺少必填列：${missing.map((item) => item.label).join('、')}`
    } else if (requiresUpstream(enterpriseType) && !upstreamBatchNo) {
      // 下游环节的上游主键由服务端按批号解析，因此这里只校验"批号列是否填了"
      error = '请填写上游产品批号'
    } else {
      error = validateImportRow(enterpriseType, form)
    }
    result.push({ rowNo: rowIndex + 1, batchNo, form, error })
  }
  return result
}

/**
 * 导入行的本地校验：与 {@link validateBatchForm} 的差别是<b>不检查上游主键</b>——
 * 导入文件里只有上游批号，上游企业与批号 ID 要等服务端解析后才知道。
 */
function validateImportRow(enterpriseType: number, form: BatchForm): string | null {
  if (!hasText(form.batchNo)) {
    return '请填写产品批号'
  }
  for (const field of batchFieldsOf(enterpriseType)) {
    if (!field.required) continue
    const value = form[field.key]
    if (!hasText(value as string | number | null | undefined)) {
      return `请填写${field.label}`
    }
  }
  return null
}

function parseBoolean(raw: string): boolean {
  return ['是', 'y', 'yes', 'true', '1', '需要'].includes(raw.toLowerCase())
}

/** 数值列转数字，其余按字符串处理 */
function parseValue(enterpriseType: number, key: string, raw: string): string | number {
  const numericKeys = new Set<string>()
  for (const field of batchFieldsOf(enterpriseType)) {
    // 只有明确的数值列才转数字：加工形态这类"字典文本"（如"鱼片"）必须保持字符串
    if (field.kind === 'number' || field.kind === 'select') {
      numericKeys.add(String(field.key))
    }
  }
  if (key === 'certificateType' || key === 'sourceType') {
    return Number(raw)
  }
  if (numericKeys.has(key)) {
    const value = Number(raw)
    return Number.isNaN(value) ? raw : value
  }
  return raw
}
