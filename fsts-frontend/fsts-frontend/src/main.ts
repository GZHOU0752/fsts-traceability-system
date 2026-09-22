import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './styles/global.css'
import './styles/theme.css'
const app = createApp(App)
// 不指定 locale 时 Element Plus 走英文（分页显示 10/page、弹窗按钮为 OK/Cancel），
// 与全站中文文案不一致，这里显式切换为中文语言包。
app.use(createPinia()).use(router).use(ElementPlus, { locale: zhCn })
for (const [key, component] of Object.entries(ElementPlusIconsVue)) app.component(key, component)
app.mount('#app')
