<script setup lang="ts">
import { ref } from 'vue'
import { useECharts } from '@/composables/useECharts'
import type { ProvinceCount } from '@/types/domain'

const props = defineProps<{ data?: ProvinceCount }>()
const el = ref<HTMLElement>()

useECharts(
  el,
  () => ({
    grid: { left: 46, right: 15, top: 18, bottom: 25 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: props.data?.provinces || [],
      // 省份名较长，固定显示全部并倾斜 30°，避免 ECharts 自动隔一个隐藏一个
      axisLabel: { color: '#5a7280', fontSize: 11, interval: 0, rotate: 30 },
    },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf4f2' } } },
    series: [
      {
        type: 'bar',
        // 面板改为整行宽之后，固定 18px 会变成一排细签；用最大宽度让柱体自适应
        barMaxWidth: 46,
        data: props.data?.counts || [],
        itemStyle: { color: '#20a79a', borderRadius: [5, 5, 0, 0] },
      },
    ],
  }),
  () => props.data,
)
</script>
<template><div ref="el" class="chart chart--bar"></div></template>
