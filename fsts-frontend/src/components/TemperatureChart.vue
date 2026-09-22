<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { TemperaturePoint } from '@/types/domain'

const props = defineProps<{ points: TemperaturePoint[] }>()
const element = ref<HTMLElement>()
let chart: echarts.ECharts | undefined

function render() {
  if (!element.value) return
  chart ??= echarts.init(element.value)
  // 阈值优先取接口返回的 threshold，缺失时回落到冷冻海产品的通用判据 -18 ℃
  const threshold = props.points.find((point) => typeof point.threshold === 'number')?.threshold ?? -18
  chart.setOption({
    grid: { left: 48, right: 22, top: 30, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: props.points.map((point) => point.stageName || point.recordTime || ''),
      axisLabel: { color: '#708895', fontSize: 11 },
    },
    // 纵轴贴合实际温度区间：真实数据只在 -19.5 ~ -18.2 之间，默认刻度会把折线压成一条直线
    yAxis: {
      type: 'value',
      name: '°C',
      min: (value: { min: number; max: number }) => Math.floor(Math.min(value.min, threshold) - 1),
      max: (value: { min: number; max: number }) => Math.ceil(value.max + 1),
      splitLine: { lineStyle: { color: '#edf4f2' } },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        data: props.points.map((point) => point.temperature),
        itemStyle: { color: '#118b82' },
        areaStyle: { color: 'rgba(32,167,154,.12)' },
        // 接口文档约定曲线附带合格阈值，画出来才能一眼判断是否断链
        markLine: {
          silent: true,
          symbol: 'none',
          data: [
            {
              yAxis: threshold,
              label: { formatter: `合格阈值 ${threshold}°C`, color: '#d4564e', fontSize: 10, position: 'insideEndTop' },
              lineStyle: { color: '#d4564e', type: 'dashed', width: 1 },
            },
          ],
        },
      },
    ],
  })
}

onMounted(() => nextTick(render))
watch(() => props.points, render, { deep: true })
onBeforeUnmount(() => chart?.dispose())
</script>
<template><div ref="element" class="temperature-chart"></div></template>
