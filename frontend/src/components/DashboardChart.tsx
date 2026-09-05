import { useEffect, useRef } from 'react'
import type { EChartsOption } from 'echarts'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

// 控制台仅展示柱状、折线和环形三类图表，按需注册可避免把未使用的 ECharts 图表代码打进首屏资源。
echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

interface DashboardChartProps {
  title: string
  summary?: string
  accessibleDescription: string
  option: EChartsOption
}

/**
 * 统一承载仪表盘数据图表，负责 ECharts 生命周期、容器尺寸变化和减少动态效果偏好的兼容。
 * 页面只需提供业务数据转换后的 option，避免各图表重复编写释放与缩放逻辑。
 */
export function DashboardChart({ title, summary, accessibleDescription, option }: DashboardChartProps) {
  const elementRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const element = elementRef.current
    if (!element) return undefined

    // 无 Canvas 文本测量能力的 SSR/测试环境不能安全绘制图表；保留语义容器，交由浏览器环境完成渲染。
    const context = document.createElement('canvas').getContext('2d')
    if (!context || typeof context.measureText !== 'function') return undefined

    const chart = echarts.init(element)
    // 用户选择减少动态效果时关闭 ECharts 入场动画，避免造成不必要的视觉干扰。
    const reducedMotion = typeof window.matchMedia === 'function'
      && window.matchMedia('(prefers-reduced-motion: reduce)').matches
    chart.setOption({ ...option, animation: !reducedMotion }, { notMerge: true })

    // 侧栏伸缩、窗口变化与响应式栅格都会改变图表可用宽度，因此观察实际容器而非只监听 window。
    const observer = typeof ResizeObserver === 'undefined'
      ? undefined
      : new ResizeObserver(() => chart.resize())
    observer?.observe(element)
    const resize = () => chart.resize()
    window.addEventListener('resize', resize)

    return () => {
      observer?.disconnect()
      window.removeEventListener('resize', resize)
      chart.dispose()
    }
  }, [option])

  return (
    <section className="dashboard-chart" role="region" aria-label={title}>
      <header className="dashboard-chart-header">
        <h3>{title}</h3>
        {summary && <span>{summary}</span>}
      </header>
      <p className="dashboard-chart-accessible-description">{accessibleDescription}</p>
      <div ref={elementRef} className="dashboard-chart-canvas" />
    </section>
  )
}
