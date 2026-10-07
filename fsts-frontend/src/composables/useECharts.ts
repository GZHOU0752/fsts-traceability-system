import { nextTick, onBeforeUnmount, onMounted, watch, type Ref } from 'vue'
import echarts, { type EChartsCoreOption, type EChartsType } from '@/lib/echarts'

/**
 * 图表生命周期复用。
 *
 * <p>四个图表组件原本各自重复实现 init / watch / dispose。这里统一收口，并补上
 * 两个原来缺失的点：
 * <ul>
 *   <li>容器尺寸变化时跟随 resize（窗口缩放、侧边栏折叠后图表不再被拉变形）；</li>
 *   <li>watch 默认浅比较：原来对 props 数组做 deep watch，数据量大时每次上报
 *       都要递归遍历整个对象树，这里只关心引用替换。</li>
 * </ul>
 */
export function useECharts(
  element: Ref<HTMLElement | undefined>,
  buildOption: () => EChartsCoreOption,
  watchSource: () => unknown,
) {
  let chart: EChartsType | undefined
  let resizeObserver: ResizeObserver | undefined
  let disposed = false

  function render() {
    if (disposed || !element.value) return
    chart ??= echarts.init(element.value)
    chart.setOption(buildOption())
  }

  function resize() {
    chart?.resize()
  }

  onMounted(() => {
    void nextTick(() => {
      render()
      if (typeof ResizeObserver === 'undefined' || !element.value) return
      resizeObserver = new ResizeObserver(resize)
      resizeObserver.observe(element.value)
    })
  })

  watch(watchSource, () => {
    void nextTick(render)
  })

  onBeforeUnmount(() => {
    disposed = true
    resizeObserver?.disconnect()
    chart?.dispose()
    chart = undefined
  })
}
