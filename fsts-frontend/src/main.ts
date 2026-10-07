import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/global.css'
import './styles/theme.css'
const app = createApp(App)
// Element Plus 改为按需注册（见 vite.config.ts），因此不再 app.use(ElementPlus)；
// 中文语言包改由 App.vue 的 <el-config-provider> 注入，否则分页/弹窗会回落英文。
app.use(createPinia()).use(router)
app.mount('#app')
