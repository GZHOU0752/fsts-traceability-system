<script setup lang="ts">
import { ref } from 'vue'
import { useECharts } from '@/composables/useECharts'
import type { ProvinceDistribution, TypeDistribution } from '@/types/domain'

const props = defineProps<{ data: (ProvinceDistribution | TypeDistribution)[] }>()
const el = ref<HTMLElement>()

useECharts(
  el,
  () => ({
    tooltip: { trigger: 'item' },
    // 省份分布有 9 项，收紧图例图标与间距后基本能在一行放下，减少滚动翻页
    legend: {
      bottom: 0,
      type: 'scroll',
      itemGap: 12,
      itemWidth: 9,
      itemHeight: 9,
      textStyle: { color: '#5a7280', fontSize: 11 },
    },
    series: [
      {
        type: 'pie',
        radius: ['48%', '70%'],
        center: ['50%', '42%'],
        label: { show: false },
        data: props.data.map((item) => ({
          name: 'provinceName' in item ? item.provinceName : item.enterpriseTypeName,
          value: item.count,
        })),
      },
    ],
    color: ['#118b82', '#ed775c', '#d9932f', '#4f84a7', '#91b9b2', '#d2a3a0'],
  }),
  () => props.data,
)
</script>
<template><div ref="el" class="chart chart--donut"></div></template>
