<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh, ArrowUp, OfficeBuilding, Ship, Box, Shop, Goods, WarningFilled, Bell, Ticket } from '@element-plus/icons-vue'
import { showError } from '@/lib/feedback'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api/admin'
import type { BusinessMetrics, Overview, RegisterTrend, ProvinceDistribution, ProvinceCount, TypeDistribution } from '@/types/domain'
import PageHeader from '@/components/PageHeader.vue'
import RegisterTrendChart from '@/components/charts/RegisterTrendChart.vue'
import DistributionDonutChart from '@/components/charts/DistributionDonutChart.vue'
import ProvinceBarChart from '@/components/charts/ProvinceBarChart.vue'

const loading = ref(false); const overview = ref<Overview>(); const trend = ref<RegisterTrend>(); const provinces = ref<ProvinceDistribution[]>([]); const provinceCount = ref<ProvinceCount>(); const types = ref<TypeDistribution[]>([]); const business = ref<BusinessMetrics>()
async function load() { loading.value = true; try { [overview.value, trend.value, provinces.value, provinceCount.value, types.value, business.value] = await Promise.all([adminApi.overview(), adminApi.registerTrend(), adminApi.provinceDistribution(), adminApi.provinceCount(), adminApi.typeDistribution(), adminApi.business()]) } catch (error) { showError(error instanceof Error ? error.message : '统计数据加载失败') } finally { loading.value = false } }
onMounted(load)
const cards = [{ key: 'totalCount', label: '登记企业', icon: OfficeBuilding, tone: 'teal', showToday: true }, { key: 'fishingCount', label: '捕捞 / 养殖', icon: Ship, tone: 'coral', showToday: false }, { key: 'processingCount', label: '加工节点', icon: Box, tone: 'amber', showToday: false }, { key: 'retailCount', label: '零售节点', icon: Shop, tone: 'blue', showToday: false }] as const
// 业务指标（接口 7.6）：与企业维度互补，回答"平台上正在发生什么"
const businessCards = [{ key: 'activeBatchCount', label: '在架批号', icon: Goods, tone: 'teal' }, { key: 'coldChainAbnormalCount', label: '冷链异常批次', icon: WarningFilled, tone: 'coral' }, { key: 'pendingConfirmCount', label: '待处理确认', icon: Bell, tone: 'amber' }, { key: 'validTraceCodeCount', label: '有效溯源码', icon: Ticket, tone: 'blue' }] as const
const businessHints = computed<Record<string, string>>(() => ({
  activeBatchCount: `共 ${business.value?.totalBatchCount ?? 0} 个批号`,
  coldChainAbnormalCount: (business.value?.coldChainAbnormalCount ?? 0) > 0 ? '存在温度异常，请核查' : '温度记录全部合格',
  pendingConfirmCount: (business.value?.pendingConfirmCount ?? 0) > 0 ? '上游企业待处理' : '当前无积压',
  validTraceCodeCount: '消费者可正常查询',
}))
</script>
<template><div class="dashboard-view"><PageHeader title="运营总览"><template #default><el-button :loading="loading" :icon="Refresh" @click="load">刷新数据</el-button></template></PageHeader><div v-loading="loading" class="metric-grid"><article v-for="card in cards" :key="card.key" class="metric-card" :class="`metric-card--${card.tone}`"><div class="metric-card__icon"><el-icon><component :is="card.icon" /></el-icon></div><span>{{ card.label }}</span><strong>{{ overview?.[card.key] ?? '--' }}</strong><small v-if="card.showToday"><ArrowUp /> 今日新增 {{ overview?.todayRegisterCount ?? 0 }}</small></article></div><div v-loading="loading" class="metric-grid"><article v-for="card in businessCards" :key="card.key" class="metric-card" :class="`metric-card--${card.tone}`"><div class="metric-card__icon"><el-icon><component :is="card.icon" /></el-icon></div><span>{{ card.label }}</span><strong>{{ business?.[card.key] ?? '--' }}</strong><small>{{ businessHints[card.key] }}</small></article></div><div class="dashboard-grid"><section class="data-panel chart-panel chart-panel--wide"><div class="data-panel__head"><div><strong>企业注册趋势</strong></div><el-tag type="success" effect="plain">累计 {{ trend?.total ?? 0 }}</el-tag></div><RegisterTrendChart :data="trend" /></section><section class="data-panel chart-panel"><div class="data-panel__head"><div><strong>企业类型分布</strong></div></div><DistributionDonutChart :data="types" /></section><section class="data-panel chart-panel"><div class="data-panel__head"><div><strong>省份分布</strong></div></div><DistributionDonutChart :data="provinces" /></section><section class="data-panel chart-panel chart-panel--wide"><div class="data-panel__head"><div><strong>区域企业数量</strong></div></div><ProvinceBarChart :data="provinceCount" /></section></div></div></template>
