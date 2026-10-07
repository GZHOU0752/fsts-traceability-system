<script setup lang="ts">import { computed } from 'vue'; import { useRoute } from 'vue-router'; import zhCn from 'element-plus/es/locale/lang/zh-cn'; import PublicLayout from '@/layouts/PublicLayout.vue'; import { Monitor } from '@element-plus/icons-vue'; const route = useRoute(); const isBare = computed(() => Boolean(route.meta.public && route.meta.bare))</script>
<template>
  <!-- 按需引入后没有 app.use(ElementPlus, { locale }) 这个全局配置入口，
       改用 ElConfigProvider 注入中文语言包，保证分页、弹窗按钮仍是中文。 -->
  <el-config-provider :locale="zhCn">
    <PublicLayout v-if="route.meta.public && !route.meta.bare"><router-view /></PublicLayout>
    <template v-else-if="isBare"><router-view /><router-link class="bare-back" to="/trace"><el-icon><Monitor /></el-icon>消费者溯源查询</router-link></template>
    <router-view v-else />
  </el-config-provider>
</template>
