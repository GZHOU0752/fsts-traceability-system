import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需注册：只把模板里真正用到的组件/指令（含 v-loading）打进 JS。
    // importStyle: false —— 组件样式仍由 global.css 统一引入完整主题
    // （CSS gzip 后仅约 54KB，再拆成几十个小文件反而增加请求数），这里只解决 JS 体积。
    Components({
      dts: false,
      resolvers: [ElementPlusResolver({ importStyle: false })],
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    // 供应商分包：echarts 与 Element Plus 体积大、版本稳定，单独成 chunk 后
    // 业务代码发版时浏览器仍能复用缓存的 vendor 文件，且两者可并行下载。
    // 注意不设 node_modules 兜底分组，否则 jsqr / qrcode 这类按需加载的库
    // 会被并进首屏 vendor，反而抵消懒加载收益。
    rolldownOptions: {
      output: {
        advancedChunks: {
          groups: [
            { name: 'echarts', test: /node_modules[\\/](echarts|zrender)[\\/]/ },
            { name: 'element-plus', test: /node_modules[\\/](element-plus|@element-plus)[\\/]/ },
          ],
        },
      },
    },
    chunkSizeWarningLimit: 800,
  },
  server: {
    port: 5173,
    // 监听局域网：手机扫码打开的是本机内网地址，只监听 localhost 时手机连不上
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
