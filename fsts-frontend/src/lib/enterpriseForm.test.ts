import { describe, expect, it } from 'vitest'
import {
  createEmptyEnterpriseForm,
  licenseFieldsOf,
  resetLicenseFields,
  toEnterpriseForm,
  validateEnterpriseForm,
} from './enterpriseForm'
import type { EnterpriseDetail, EnterpriseForm } from '@/types/domain'

/**
 * 对齐后端 EnterpriseValidator 的必填规则。
 * 之前管理端新增企业表单完全没有资质字段，提交必然 400。
 */
describe('enterprise form schema', () => {
  function baseForm(): EnterpriseForm {
    return {
      ...createEmptyEnterpriseForm(),
      enterpriseName: '青岛远洋渔业有限公司',
      loginName: 'qd_yuanye',
      creditCode: '91370200MA3D1K2X8A',
      provinceCode: '370000',
      cityCode: '370200',
    }
  }

  it('资质字段按企业类型返回，与接口文档 6.3 的附加字段表一致', () => {
    expect(licenseFieldsOf(1).map((field) => String(field.key))).toEqual(['fisheryLicenseNo', 'aquacultureLicenseNo', 'fryLicenseNo'])
    expect(licenseFieldsOf(2).map((field) => String(field.key))).toEqual(['foodProductionLicenseNo', 'exportFilingNo'])
    expect(licenseFieldsOf(2).find((field) => field.key === 'foodProductionLicenseNo')?.required).toBe(true)
    expect(licenseFieldsOf(4).map((field) => String(field.key))).toEqual(['foodBusinessLicenseNo', 'displayEquipmentInfo'])
    expect(licenseFieldsOf(undefined)).toEqual([])
  })

  it('捕捞与养殖企业要求捕捞证与养殖证至少一项', () => {
    const form = baseForm()
    form.enterpriseType = 1
    expect(validateEnterpriseForm(form)).toBe('捕捞与养殖企业须至少填写渔业捕捞许可证编号或水域滩涂养殖证编号')

    form.aquacultureLicenseNo = 'YZQZ-2026-2102-0033'
    expect(validateEnterpriseForm(form)).toBeNull()

    form.fisheryLicenseNo = 'LYQZ-2025-3702-0071'
    expect(validateEnterpriseForm(form)).toBeNull()
  })

  it('加工企业与经营类企业要求各自的许可证', () => {
    const processing = baseForm()
    processing.enterpriseType = 2
    expect(validateEnterpriseForm(processing)).toBe('请填写食品生产许可证编号')
    processing.foodProductionLicenseNo = 'SC11337060200136'
    expect(validateEnterpriseForm(processing)).toBeNull()

    const wholesale = baseForm()
    wholesale.enterpriseType = 3
    expect(validateEnterpriseForm(wholesale)).toBe('请填写食品经营许可证编号')
    wholesale.foodBusinessLicenseNo = 'JY13101150028641'
    expect(validateEnterpriseForm(wholesale)).toBeNull()
  })

  it('必填公共字段缺失时给出可读提示', () => {
    const form = baseForm()
    form.enterpriseName = ''
    expect(validateEnterpriseForm(form)).toBe('请填写企业名称')
    form.enterpriseName = '某企业'
    form.provinceCode = ''
    expect(validateEnterpriseForm(form)).toBe('请选择省份')
  })

  it('切换企业类型会清空资质字段，避免把无关证件提交上去', () => {
    const form = baseForm()
    form.foodBusinessLicenseNo = 'JY13101150028641'
    resetLicenseFields(form)
    expect(form.foodBusinessLicenseNo).toBeUndefined()
    expect(form.fisheryLicenseNo).toBeUndefined()
  })

  it('回显时只保留本类型的资质字段', () => {
    const detail = {
      id: '2',
      enterpriseType: 2,
      enterpriseName: '烟台海珍冷冻食品有限公司',
      loginName: 'yt_haizhen',
      creditCode: '91370600MA3D2L4Y6B',
      provinceCode: '370000',
      cityCode: '370600',
      status: 1,
      foodProductionLicenseNo: 'SC11337060200136',
      fisheryLicenseNo: 'LYQZ-2025-3702-0071',
    } as unknown as EnterpriseDetail

    const form = toEnterpriseForm(detail)
    expect(form.enterpriseType).toBe(2)
    expect(form.foodProductionLicenseNo).toBe('SC11337060200136')
    // 类型 2 的详情里即使残留捕捞证字段，回显表单也不应带出来
    expect(form.fisheryLicenseNo).toBe('')
  })

  it('回显必须带上省市代码，否则编辑保存会被必填校验拦住', () => {
    const detail = {
      id: '4',
      enterpriseType: 4,
      enterpriseName: '上海佳鲜生鲜超市有限公司',
      loginName: 'sh_jiaxian',
      creditCode: '91310115MA1H4N9Q8D',
      provinceCode: '310000',
      provinceName: '上海市',
      cityCode: '310100',
      cityName: '上海市',
      status: 1,
      foodBusinessLicenseNo: 'JY13101150031972',
    } as unknown as EnterpriseDetail

    const form = toEnterpriseForm(detail)
    expect(form.provinceCode).toBe('310000')
    expect(form.cityCode).toBe('310100')
    expect(validateEnterpriseForm(form)).toBeNull()
  })
})
