import { defineConfig, devices } from '@playwright/test'

/**
 * 端到端测试配置。
 *
 * <p>为什么要有它：单元测试（vitest）只覆盖纯函数，接口冒烟只覆盖 HTTP 层，
 * 而本项目真实出现过的缺陷（表单缺字段导致必然 400、按钮点了没反应、
 * 某类企业进某个菜单必然报错）只有"真的点一遍"才会暴露。
 *
 * <p>不在这里自动拉起服务：前后端由 start-services.ps1 统一管理（含数据库检查），
 * 测试只负责在已就绪的环境上跑业务流；未就绪时会在第一个用例超时失败。
 * 通过环境变量切换目标地址：E2E_BASE_URL（默认 http://localhost:5173）。
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // 用例之间会改动批号数据，必须串行执行
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    // 优先复用本机已安装的 Chrome：避免为跑一次 e2e 再下载 ~150MB 的 Playwright 浏览器。
    // 若本机没有 Chrome，删掉这一行并执行 `npx playwright install chromium` 即可。
    channel: 'chrome',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
