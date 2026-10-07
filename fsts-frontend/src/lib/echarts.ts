/**
 * ECharts 按需注册入口。
 *
 * <p>原实现四个图表组件都写 `import * as echarts from 'echarts'`，
 * 会把全部图表类型、组件与渲染器打进产物（构建后约 1.1 MB）。
 * 这里只注册项目实际用到的三类图表 + 五个组件 + Canvas 渲染器，
 * 其余代码由打包器 tree-shaking 剔除。
 */
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TooltipComponent,
  CanvasRenderer,
])

export default echarts
export type { EChartsCoreOption, EChartsType } from 'echarts/core'
