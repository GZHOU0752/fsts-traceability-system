import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // 只跑单元测试：e2e 目录由 @playwright/test 单独驱动，
    // 否则 vitest 会把 *.spec.ts 也当成单测收集进来，因缺少 Playwright 运行时而整体失败
    include: ['src/**/*.{test,spec}.ts'],
  },
})
