<script setup lang="ts">
import { ref } from 'vue'
import { useECharts } from '@/composables/useECharts'
import type { RegisterTrend } from '@/types/domain'

const props = defineProps<{ data?: RegisterTrend }>()
const el = ref<HTMLElement>()

useECharts(
  el,
  () => ({
    grid: { left: 30, right: 18, top: 22, bottom: 24 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', boundaryGap: false, data: props.data?.months || [] },
    // 注册企业数是整数，默认刻度会出现 0.5 / 1.5 这种没有意义的企业数
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf4f2' } } },
    series: [
      {
        type: 'line',
        smooth: true,
        data: props.data?.counts || [],
        symbol: 'circle',
        symbolSize: 7,
        itemStyle: { color: '#ed775c' },
        lineStyle: { width: 3, color: '#ed775c' },
        areaStyle: { color: 'rgba(237,119,92,.10)' },
      },
    ],
  }),
  () => props.data,
)
</script>
<template><div ref="el" class="chart chart--trend"></div></template>
