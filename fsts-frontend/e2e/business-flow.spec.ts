import { expect, test, type Locator, type Page } from '@playwright/test'

/**
 * 关键业务流的端到端测试。
 *
 * <p>覆盖的是"界面能不能真的点通"，而不是接口是否返回 200：
 * 本项目踩过的坑（表单缺字段导致必然 400、下架入口点不到、某类企业进某菜单必然报错）
 * 都发生在这一层。接口层断言见 fsts-backend/scripts/api-smoke-test.ps1。
 *
 * <p>前置：start-services.ps1 已把前后端拉起，且演示数据已导入。
 */
test.describe.configure({ mode: 'serial' })

const SUFFIX = String(Math.floor(Math.random() * 900000) + 100000)
const PASSWORD = '123456'

async function loginAdmin(page: Page) {
  await page.goto('/admin/login')
  await page.getByRole('button', { name: '进入管理端' }).click()
  await expect(page).toHaveURL(/\/admin\/dashboard/)
}

async function loginEnterprise(page: Page, loginName: string) {
  await page.goto('/enterprise/login')
  await page.getByRole('textbox', { name: '企业登录账号' }).fill(loginName)
  await page.getByRole('textbox', { name: '登录密码' }).fill(PASSWORD)
  await page.getByRole('button', { name: '进入企业端' }).click()
  await expect(page).toHaveURL(/\/enterprise\//)
}

/**
 * 选中 Element Plus 的非筛选型下拉（企业类型 / 来源类型 / 证明类型 / 加工形态）。
 *
 * <p>不能直接点 role=combobox：Element Plus 的选择器把可见文本放在一个覆盖层里，
 * 点内部 input 会被 "intercepts pointer events" 挡住；正确做法是点外层
 * .el-select__wrapper（真实用户点的也是它）。
 */
async function pickSelect(page: Page, scope: Locator, label: string, optionName: string | RegExp) {
  const item = formItem(scope, label)
  await item.locator('.el-select__wrapper').first().click()
  await page.getByRole('option', { name: optionName }).first().click()
}

/**
 * 选中筛选型下拉（省份 / 城市 / 上游企业 / 上游批号）。
 *
 * <p>做法贴近真实用户：点开下拉（force 跳过浮层命中检查）→ 直接键入关键字筛选
 * → 回车选中第一项 → 断言选中结果。最后一步很关键：如果不校验，一旦选择没生效，
 * 错误会推迟到"提交后没反应"，排查成本高得多。
 */
async function pickFilterableSelect(page: Page, scope: Locator, label: string, text: string) {
  const item = formItem(scope, label)
  await item.locator('.el-select__wrapper').first().click({ force: true })
  // 分步断言：下拉必须先打开、筛选必须命中目标项，最后确认值已提交。
  // 否则一旦某一步静默失效，错误会推迟到"提交后没反应"，排查成本高得多。
  await expect(page.getByRole('option').first()).toBeVisible()
  await page.keyboard.type(text, { delay: 20 })
  // 筛选后直接点唯一匹配项：比"回车选中高亮项"可靠——回车依赖高亮索引，
  // 列表被筛选后高亮项可能已被过滤掉，就会出现"看起来输了、其实没选上"。
  // force 跳过"元素稳定"检查：Element Plus 的选项列表在筛选后仍有过渡动画，
  // 会一直被判为不稳定；点击结果由下一行的断言兜底，不会掩盖真实失败。
  await page.getByRole('option', { name: text }).first().click({ force: true })
  await expect(item.locator('.el-select__wrapper').first()).toContainText(text)
}

/** 按表单标签定位 el-form-item（label 文本是精确匹配，避免"上游企业"命中"上游产品批号"） */
function formItem(scope: Locator, label: string): Locator {
  return scope.locator('.el-form-item').filter({ has: scope.page().getByText(label, { exact: true }) }).first()
}

/** 企业端登录令牌：用于接口侧复核界面操作的结果 */
async function enterpriseToken(page: Page, loginName: string): Promise<string> {
  const response = await page.request.post('/api/enterprise/auth/login', {
    data: { loginName, password: PASSWORD },
  })
  const body = await response.json()
  return body.data.token
}

test('管理端：大屏业务指标 + 编辑企业时按类型渲染资质字段并保存', async ({ page }) => {
  await loginAdmin(page)
  // 业务指标卡片（接口 7.6）随大屏一起加载
  await expect(page.getByText('在架批号')).toBeVisible()
  await expect(page.getByText('冷链异常批次')).toBeVisible()

  await page.goto('/admin/enterprises')
  // 演示数据里的零售商：打开编辑对话框验证"资质字段按企业类型渲染 + 表单可保存"
  // 列表按注册时间倒序，目标企业不在第一页，先用模糊查询定位（顺带覆盖查询功能）
  await page.getByRole('textbox', { name: '企业名称' }).fill('佳鲜生')
  await page.getByRole('button', { name: '查询' }).click()
  const row = page.getByRole('row', { name: /上海佳鲜生鲜超市有限公司/ })
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '编辑' }).click()
  const dialog = page.getByRole('dialog', { name: '编辑企业' })
  await expect(dialog.getByText('资质证件（零售终端）')).toBeVisible()
  const license = dialog.getByRole('textbox', { name: '食品经营许可证编号' })
  // 资质字段来自详情接口回显（过去新建企业时前端根本没有这些字段）
  await expect(license).toHaveValue('JY13101150031972')

  await dialog.getByRole('textbox', { name: '联系人' }).fill('端到端测试联系人')
  await dialog.getByRole('button', { name: '保存企业' }).click()
  await expect(page.getByText('企业资料已更新')).toBeVisible()

  // 接口侧复核：修改确实落库
  const adminToken = (
    await (await page.request.post('/api/admin/auth/login', { data: { loginName: 'admin', password: PASSWORD } })).json()
  ).data.token
  const auth = { Authorization: `Bearer ${adminToken}` }
  const list = await (
    await page.request.get(`/api/admin/enterprises?enterpriseName=${encodeURIComponent('上海佳鲜生鲜超市有限公司')}`, { headers: auth })
  ).json()
  expect(list.data.records[0].contactPerson).toBe('端到端测试联系人')

  // 还原演示数据：把联系人改回原值（演示数据里是孙涛）
  const detail = await (
    await page.request.get(`/api/admin/enterprises/${list.data.records[0].id}`, { headers: auth })
  ).json()
  await page.request.put(`/api/admin/enterprises/${list.data.records[0].id}`, {
    headers: auth,
    data: { ...detail.data, contactPerson: '孙涛' },
  })
})

test('企业端：捕捞企业新建批号（含证明类型）并在详情看到状态流转', async ({ page }) => {
  await loginEnterprise(page, 'qd_yuanye')
  // 链条源头没有上游企业，菜单里不应出现"上游数据"（过去点进去必然 400）
  await expect(page.getByRole('link', { name: '上游数据' })).toHaveCount(0)

  await page.getByRole('button', { name: '新建批号' }).click()
  const dialog = page.getByRole('dialog', { name: '新建批号' })
  const batchNo = `YY-E2E-${SUFFIX}`
  await dialog.getByRole('textbox', { name: '产品批号' }).fill(batchNo)
  await dialog.getByRole('textbox', { name: '产品品种' }).fill('端到端测试带鱼')
  await pickSelect(page, dialog, '来源类型', '捕捞')
  await dialog.getByRole('combobox', { name: '捕捞 / 养殖日期' }).fill('2026-10-15')
  await page.keyboard.press('Enter')
  // 证明类型是过去前端表单里缺失、导致后端必然 400 的字段
  await pickSelect(page, dialog, '证明类型', '渔获物上岸证明')
  await dialog.getByRole('textbox', { name: '证明编号' }).fill(`CERT-E2E-${SUFFIX}`)
  await dialog.getByRole('spinbutton', { name: '起运温度' }).fill('-19.5')
  await dialog.getByRole('button', { name: '保存批号' }).click()

  await expect(page.getByText(batchNo)).toBeVisible()
  const token = await enterpriseToken(page, 'qd_yuanye')
  const list = await (
    await page.request.get(`/api/enterprise/batches?batchStatus=1&batchNo=${batchNo}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
  ).json()
  expect(list.data.total).toBe(1)
  expect(list.data.records[0].productVariety).toBe('端到端测试带鱼')

  // 详情抽屉里的状态流转时间线（接口 10.2 statusLogs）
  await page.getByRole('button', { name: '详情' }).first().click()
  await expect(page.getByRole('dialog', { name: '批号详情' }).getByText('状态流转')).toBeVisible()
  await page.keyboard.press('Escape')

  await page.request.delete(`/api/enterprise/batches/${list.data.records[0].id}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
})

test('企业端：加工企业选上游批号 → 发起确认 → 撤回（接口 10.9 / 11.5）', async ({ page }) => {
  await loginEnterprise(page, 'yt_haizhen')

  await page.getByRole('button', { name: '新建批号' }).click()
  const dialog = page.getByRole('dialog', { name: '新建批号' })
  const batchNo = `JG-E2E-${SUFFIX}`
  await dialog.getByRole('textbox', { name: '产品批号' }).fill(batchNo)
  // 上游企业与上游批号：下拉只应出现合法的上游（捕捞企业）与其已发布批号
  await pickFilterableSelect(page, dialog, '上游企业', '青岛远洋渔业有限公司')
  await pickFilterableSelect(page, dialog, '上游产品批号', 'YY20260901001')
  // 品种由上游带出，且应处于只读状态
  await expect(dialog.getByRole('textbox', { name: '产品品种（上游带出）' })).toHaveValue('带鱼')

  await pickSelect(page, dialog, '加工形态', '鱼片')
  await dialog.getByRole('textbox', { name: '出厂检验报告编号' }).fill(`CYJ-E2E-${SUFFIX}`)
  await dialog.getByRole('spinbutton', { name: '速冻中心温度' }).fill('-36')
  await dialog.getByRole('spinbutton', { name: '出厂温度' }).fill('-19.2')
  // el-checkbox 的可见文本在 label 里，直接点 label 比按 role=checkbox 定位更稳
  await dialog.locator('.el-checkbox').filter({ hasText: '同时向上游发送' }).click()
  await dialog.getByRole('button', { name: '保存批号' }).click()

  // 保存后进入"待确认"分组，行内出现"撤回"
  // el-radio-button 的真实 radio 输入是隐藏的，点它的文字标签才有效
  await page.getByRole('radiogroup').getByText('待确认', { exact: true }).click()
  await expect(page.getByText(batchNo)).toBeVisible()
  await page.getByRole('button', { name: '撤回' }).first().click()
  await page.getByRole('button', { name: '确认撤回' }).click()
  await expect(page.getByText(batchNo)).toHaveCount(0, { timeout: 10_000 })

  // 撤回后批号回到"新建"，可被删除（这也是清理步骤）
  const token = await enterpriseToken(page, 'yt_haizhen')
  const list = await (
    await page.request.get(`/api/enterprise/batches?batchStatus=1&batchNo=${batchNo}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
  ).json()
  expect(list.data.total).toBe(1)
  await page.request.delete(`/api/enterprise/batches/${list.data.records[0].id}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
})

test('消费者端：输入溯源码看到完整冷链；零售商菜单按环节收敛', async ({ page }) => {
  await page.goto('/trace')
  await page.getByRole('textbox', { name: '或直接输入溯源码' }).fill('FSTS-20260915-SH-0001')
  await page.getByRole('button', { name: '按溯源码查询' }).click()
  await expect(page.getByRole('heading', { name: '全链路流转' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '冷链温度曲线' })).toBeVisible()
  await expect(page.getByText('冷链未断链')).toBeVisible()

  // 零售商处于链条末端：不参与下游确认，因此没有"进场确认"菜单
  await loginEnterprise(page, 'sh_jiaxian')
  await expect(page.getByRole('link', { name: '进场确认' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: '上游数据' })).toBeVisible()
})
