import { describe, expect, it } from 'vitest'
import {
  batchFieldsOf,
  canPublishDirectly,
  createEmptyBatchForm,
  hasTraceCode,
  requiresUpstream,
  toBatchForm,
  validateBatchForm,
} from './batchForm'
import type { BatchDetail } from '@/types/domain'

/**
 * 这些断言锁定的不是"前端实现细节"，而是与后端 BatchValidator 的契约。
 * 之前正是这里对不上（表单缺 certificateType / 缺上游选择），
 * 导致四类企业新建批号全部被后端 400 拒绝。
 */
describe('batch form schema', () => {
  it('要求链条源头填写证明类型，与 FishingBatchValidator 一致', () => {
    const fields = batchFieldsOf(1)
    const keys = fields.map((field) => String(field.key))
    expect(keys).toContain('certificateType')
    expect(keys).toContain('certificateNo')
    expect(fields.find((field) => field.key === 'certificateType')?.required).toBe(true)
    expect(validateBatchForm(1, { batchNo: 'YY2026', productVariety: '带鱼', sourceType: 2, catchBreedDate: '2026-09-01', certificateNo: 'CERT-1', departureTemp: -19 }))
      .toBe('请填写证明类型')
  })

  it('类型 2/3/4 必须选上游企业与上游批号', () => {
    expect(requiresUpstream(1)).toBe(false)
    expect(requiresUpstream(2)).toBe(true)
    expect(requiresUpstream(3)).toBe(true)
    expect(requiresUpstream(4)).toBe(true)

    expect(validateBatchForm(2, { batchNo: 'JG2026' })).toBe('请选择上游企业')
    expect(validateBatchForm(2, { batchNo: 'JG2026', upstreamEnterpriseId: '1' })).toBe('请选择上游产品批号')
    expect(validateBatchForm(2, { batchNo: 'JG2026', upstreamEnterpriseId: '1', upstreamBatchId: '1' })).toBe('请填写加工形态')
  })

  it('类型 3/4 的必填项与各自环节记录一致', () => {
    const wholesaleKeys = batchFieldsOf(3).map((field) => String(field.key))
    expect(wholesaleKeys).toEqual(expect.arrayContaining(['wholesaleDate', 'inboundTemp', 'coldStorageTemp', 'outboundTemp', 'transportToolNo']))
    const retailKeys = batchFieldsOf(4).map((field) => String(field.key))
    expect(retailKeys).toEqual(expect.arrayContaining(['shelfDate', 'displayTemp', 'saleStore']))
  })

  it('只有捕捞与养殖企业能直接发布，只有零售商有溯源码', () => {
    expect(canPublishDirectly(1)).toBe(true)
    expect([2, 3, 4].map(canPublishDirectly)).toEqual([false, false, false])
    expect(hasTraceCode(4)).toBe(true)
    expect([1, 2, 3].map(hasTraceCode)).toEqual([false, false, false])
  })

  it('新建表单按类型给出空白字段，不会带上其他环节的键', () => {
    const processing = createEmptyBatchForm(2)
    expect(processing.upstreamEnterpriseId).toBe('')
    expect(processing.processForm).toBe('')
    expect(processing.fishingLogNo).toBeUndefined()
    expect(processing.displayTemp).toBeUndefined()

    const fishing = createEmptyBatchForm(1)
    expect(fishing.sourceType).toBe(1)
    expect(fishing.fishingLogNo).toBe('')
    expect(fishing.processForm).toBeUndefined()
  })

  it('编辑回显会把环节明细从 fishingInfo 拍平到表单字段', () => {
    const detail = {
      id: '1',
      batchNo: 'YY20260901001',
      productVariety: '带鱼',
      sourceType: 2,
      batchStatus: 1,
      batchStatusName: '新建',
      fishingInfo: { catchBreedDate: '2026-09-01', certificateType: 2, certificateNo: 'CERT-1', departureTemp: -19.5 },
    } as unknown as BatchDetail

    const form = toBatchForm(detail, 1)
    expect(form.batchNo).toBe('YY20260901001')
    expect(form.catchBreedDate).toBe('2026-09-01')
    expect(form.certificateType).toBe(2)
    expect(form.departureTemp).toBe(-19.5)
  })
})
