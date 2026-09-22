<script setup lang="ts">import { Box } from '@element-plus/icons-vue'; withDefaults(defineProps<{ title?: string; description?: string }>(), { title: '暂无数据', description: '调整筛选条件后再试试' })</script>
<template><div class="empty-state"><el-icon :size="34"><Box /></el-icon><strong>{{ title }}</strong><span>{{ description }}</span><slot /></div></template>
