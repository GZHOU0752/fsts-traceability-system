import { describe, expect, it } from 'vitest'
import { importColumnsOf, parseImportMatrix } from './batchForm'

/**
 * 批量导入最容易出的问题是"列名对不上但用户看不出来"，
 * 因此这里锁定"模板列 ↔ 解析结果"的一致性，以及必填列的本地校验。
 */
describe('batch import parsing', () => {
  it('捕捞环节的模板列包含证明类型等必填项', () => {
    const keys = importColumnsOf(1).map((column) => column.key)
    expect(keys).toEqual(expect.arrayContaining(['batchNo', 'productVariety', 'sourceType', 'certificateType', 'certificateNo', 'departureTemp']))
    expect(keys).not.toContain('upstreamBatchNo')
  })

  it('下游环节用上游批号代替上游主键', () => {
    const keys = importColumnsOf(2).map((column) => column.key)
    expect(keys).toContain('upstreamBatchNo')
    expect(keys).toContain('upstreamEnterpriseName')
    expect(keys).not.toContain('upstreamEnterpriseId')
  })

  it('中文表头与字段名表头都能识别', () => {
    const columns = importColumnsOf(1)
    const header = columns.map((column) => column.label)
    const row = columns.map((column) => {
      if (column.key === 'batchNo') return 'YY-IMPORT-001'
      if (column.key === 'productVariety') return '带鱼'
      if (column.key === 'sourceType') return '2'
      if (column.key === 'catchBreedDate') return '2026-09-01'
      if (column.key === 'certificateType') return '2'
      if (column.key === 'certificateNo') return 'CERT-1'
      if (column.key === 'departureTemp') return '-18.5'
      return ''
    })
    const parsed = parseImportMatrix(1, [header, row])
    expect(parsed).toHaveLength(1)
    expect(parsed[0].error).toBeNull()
    expect(parsed[0].form.sourceType).toBe(2)
    expect(parsed[0].form.departureTemp).toBe(-18.5)

    const byField = parseImportMatrix(1, [columns.map((column) => column.key), row])
    expect(byField[0].error).toBeNull()
    expect(byField[0].form.batchNo).toBe('YY-IMPORT-001')
  })

  it('缺少必填列时给出明确提示而不是静默通过', () => {
    const parsed = parseImportMatrix(1, [['产品批号', '产品品种'], ['YY-IMPORT-002', '带鱼']])
    expect(parsed[0].error).toContain('模板缺少必填列')
  })

  it('必填单元格为空时标记该行不可导入，并给出字段名', () => {
    const columns = importColumnsOf(4)
    const header = columns.map((column) => column.label)
    const row = columns.map((column) => {
      if (column.key === 'batchNo') return 'LS-IMPORT-001'
      if (column.key === 'upstreamBatchNo') return 'PF20260910001'
      if (column.key === 'shelfDate') return '2026-09-10'
      if (column.key === 'displayTemp') return '-19'
      if (column.key === 'saleStore') return ''
      return ''
    })
    const parsed = parseImportMatrix(4, [header, row])
    expect(parsed[0].error).toBe('请填写销售门店')
  })

  it('布尔列支持是/否与 1/0', () => {
    const columns = importColumnsOf(2)
    const header = columns.map((column) => column.label)
    const row = columns.map((column) => {
      if (column.key === 'batchNo') return 'JG-IMPORT-001'
      if (column.key === 'upstreamBatchNo') return 'YY20260901001'
      if (column.key === 'processForm') return '鱼片'
      if (column.key === 'inspectionNo') return 'CYJ-1'
      if (column.key === 'quickFreezeTemp') return '-36'
      if (column.key === 'factoryTemp') return '-19'
      if (column.key === 'sendConfirmRequest') return '是'
      return ''
    })
    const parsed = parseImportMatrix(2, [header, row])
    expect(parsed[0].error).toBeNull()
    expect(parsed[0].form.sendConfirmRequest).toBe(true)
    // 加工形态是中文文本，不能被当成数字
    expect(parsed[0].form.processForm).toBe('鱼片')
  })
})
