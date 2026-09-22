<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh, ArrowUp, OfficeBuilding, Ship, Box, Shop } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api/admin'
import type { Overview, RegisterTrend, ProvinceDistribution, ProvinceCount, TypeDistribution } from '@/types/domain'
import PageHeader from '@/components/PageHeader.vue'
import RegisterTrendChart from '@/components/charts/RegisterTrendChart.vue'
import DistributionDonutChart from '@/components/charts/DistributionDonutChart.vue'
import ProvinceBarChart from '@/components/charts/ProvinceBarChart.vue'

const loading = ref(false); const overview = ref<Overview>(); const trend = ref<RegisterTrend>(); const provinces = ref<ProvinceDistribution[]>([]); const provinceCount = ref<ProvinceCount>(); const types = ref<TypeDistribution[]>([])
async function load() { loading.value = true; try { [overview.value, trend.value, provinces.value, provinceCount.value, types.value] = await Promise.all([adminApi.overview(), adminApi.registerTrend(), adminApi.provinceDistribution(), adminApi.provinceCount(), adminApi.typeDistribution()]) } catch (error) { ElMessage.error(error instanceof Error ? error.message : '统计数据加载失败') } finally { loading.value = false } }
onMounted(load)
const cards = [{ key: 'totalCount', label: '登记企业', icon: OfficeBuilding, tone: 'teal' }, { key: 'fishingCount', label: '捕捞 / 养殖', icon: Ship, tone: 'coral' }, { key: 'processingCount', label: '加工节点', icon: Box, tone: 'amber' }, { key: 'retailCount', label: '零售节点', icon: Shop, tone: 'blue' }] as const
</script>
<template><div class="dashboard-view"><PageHeader eyebrow="SYSTEM OVERVIEW" title="运营总览" description="实时掌握节点企业规模与冷链网络分布"><template #default><el-button :loading="loading" :icon="Refresh" @click="load">刷新数据</el-button></template></PageHeader><div class="metric-grid" v-loading="loading"><article v-for="card in cards" :key="card.key" class="metric-card" :class="`metric-card--${card.tone}`"><div class="metric-card__icon"><el-icon><component :is="card.icon" /></el-icon></div><span>{{ card.label }}</span><strong>{{ overview?.[card.key] ?? '--' }}</strong><small><ArrowUp /> 今日新增 {{ overview?.todayRegisterCount ?? 0 }}</small></article></div><div class="dashboard-grid"><section class="data-panel chart-panel chart-panel--wide"><div class="data-panel__head"><div><strong>企业注册趋势</strong><span>最近 12 个月</span></div><el-tag type="success" effect="plain">累计 {{ trend?.total ?? 0 }}</el-tag></div><RegisterTrendChart :data="trend" /></section><section class="data-panel chart-panel"><div class="data-panel__head"><div><strong>企业类型分布</strong><span>节点类型占比</span></div></div><DistributionDonutChart :data="types" /></section><section class="data-panel chart-panel"><div class="data-panel__head"><div><strong>省份分布</strong><span>注册企业 TOP 10</span></div></div><DistributionDonutChart :data="provinces" /></section><section class="data-panel chart-panel chart-panel--wide"><div class="data-panel__head"><div><strong>区域企业数量</strong><span>按省份统计</span></div></div><ProvinceBarChart :data="provinceCount" /></section></div></div></template>
