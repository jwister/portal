import { Button, ButtonGroup } from '@douyinfe/semi-ui'
import { IconActivity, IconCoinMoney, IconCreditCard, IconHistory, IconKey, IconPulse, IconRefresh, IconServer } from '@douyinfe/semi-icons'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { EChartsOption } from 'echarts'

import '../../i18n'
import { ConsolePageHeader } from '../../components/ConsolePageHeader'
import { MetricCard } from '../../components/MetricCard'
import { RemoteState } from '../../components/RemoteState'
import { DashboardChart } from '../../components/DashboardChart'
import { getDashboard, getDashboardAnalytics, type DashboardAnalytics, type DashboardSummary } from '../../api/portal'

type DashboardRange = '7d' | '30d'

/** 将账户原始数值格式化为控制台指标卡可直接阅读的千分位文本。 */
function formatMetric(value: number): string {
  return new Intl.NumberFormat().format(value)
}

/**
 * 判断异步统计响应是否仍属于当前页面选择；请求序号或范围任一不匹配即丢弃旧响应。
 * 该判断确保慢速 30 天响应无法覆盖用户刚选择的 7 天图表。
 */
export function isLatestDashboardRequest(
  requestId: number,
  newestRequestId: number,
  requestedRange: DashboardRange,
  activeRange: DashboardRange,
): boolean {
  return requestId === newestRequestId && requestedRange === activeRange
}

/** 将后端聚合的稳定“其他模型”标识映射为当前界面的本地化展示文本。 */
export function getDisplayModelName(modelName: string, otherModelsLabel: string): string {
  return modelName === '__other__' ? otherModelsLabel : modelName
}

/**
 * 账户数据总览页：同一刷新周期读取摘要与服务端聚合分析，并提供真实的图表和快捷操作。
 * 图表数据为空时明确显示空状态，绝不以浏览器端模拟数据替代账户真实统计。
 */
export function DashboardPage() {
  const { t } = useTranslation()
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [analytics, setAnalytics] = useState<DashboardAnalytics | null>(null)
  const [range, setRange] = useState<DashboardRange>('30d')
  const [revision, setRevision] = useState(0)
  const [failed, setFailed] = useState(false)
  // 请求序号和当前范围独立于渲染状态保存，防止网络较慢的旧请求在范围切换后覆盖最新图表。
  const requestEpoch = useRef(0)
  const activeRange = useRef<DashboardRange>('30d')

  const load = useCallback(() => {
    const requestId = ++requestEpoch.current
    setFailed(false)
    setSummary(null)
    setAnalytics(null)
    // 摘要与图表共用一次刷新周期，避免在用户切换统计范围时显示不同时间点的账户状态。
    void Promise.all([getDashboard(), getDashboardAnalytics(range)])
      .then(([nextSummary, nextAnalytics]) => {
        if (!isLatestDashboardRequest(requestId, requestEpoch.current, range, activeRange.current)) return
        setSummary(nextSummary)
        setAnalytics(nextAnalytics)
      })
      .catch(() => {
        if (isLatestDashboardRequest(requestId, requestEpoch.current, range, activeRange.current)) setFailed(true)
      })
  }, [range, revision])

  useEffect(() => {
    load()
  }, [load])

  // 在请求未完成时保留空数组作为 option 输入，确保 React 每次渲染调用 Hook 的顺序恒定。
  const dailyUsage = analytics?.dailyUsage ?? []
  const topModels = analytics?.topModels ?? []
  const tokenUsage = analytics?.tokenUsage ?? []

  // 双轴图把额度和请求量放在同一时间轴上，便于识别高请求但低消耗的调用模式。
  const trendOption = useMemo<EChartsOption>(() => ({
    color: ['#2f6fed', '#15a57d'],
    grid: { top: 42, right: 40, bottom: 30, left: 52 },
    legend: { data: [t('dashboard.quotaSeries'), t('dashboard.requestSeries')], top: 0 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', boundaryGap: true, data: dailyUsage.map((item) => item.date.slice(5)) },
    yAxis: [{ type: 'value', name: t('dashboard.quotaAxis'), splitLine: { lineStyle: { color: '#edf1f6' } } }, { type: 'value', name: t('dashboard.requestAxis'), splitLine: { show: false } }],
    series: [
      { name: t('dashboard.quotaSeries'), type: 'bar', barMaxWidth: 24, data: dailyUsage.map((item) => item.quota), itemStyle: { borderRadius: [5, 5, 0, 0] } },
      { name: t('dashboard.requestSeries'), type: 'line', yAxisIndex: 1, smooth: true, data: dailyUsage.map((item) => item.requestCount), symbolSize: 7 },
    ],
  }), [dailyUsage, t])

  const modelOption = useMemo<EChartsOption>(() => ({
    color: ['#2f6fed', '#15a57d', '#f0aa30', '#8a6ee8', '#e66f8c', '#7f8da3'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{ type: 'pie', radius: ['53%', '76%'], center: ['50%', '43%'], avoidLabelOverlap: true, label: { show: false }, data: topModels.map((item) => ({ name: getDisplayModelName(item.modelName, t('dashboard.otherModels')), value: item.quota })) }],
  }), [topModels, t])

  const tokenOption = useMemo<EChartsOption>(() => ({
    color: ['#8a6ee8'],
    grid: { top: 24, right: 20, bottom: 30, left: 52 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', boundaryGap: false, data: tokenUsage.map((item) => item.date.slice(5)) },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf1f6' } } },
    series: [{ name: t('dashboard.tokenUsage'), type: 'line', smooth: true, symbol: 'none', data: tokenUsage.map((item) => item.tokenUsage), areaStyle: { color: 'rgba(138, 110, 232, .2)' }, lineStyle: { width: 3 } }],
  }), [tokenUsage, t])

  // Canvas 图表无法被屏幕阅读器逐项朗读，因此同步生成与实际 option 完全对应的文字数据摘要。
  const trendAccessibleDescription = dailyUsage.map((item) => t('dashboard.trendA11y', {
    date: item.date,
    quota: formatMetric(item.quota),
    requests: formatMetric(item.requestCount),
  })).join('; ')
  const modelsAccessibleDescription = topModels.map((item) => t('dashboard.modelA11y', {
    model: getDisplayModelName(item.modelName, t('dashboard.otherModels')),
    quota: formatMetric(item.quota),
  })).join('; ')
  const tokensAccessibleDescription = tokenUsage.map((item) => t('dashboard.tokenA11y', {
    date: item.date,
    tokens: formatMetric(item.tokenUsage),
  })).join('; ')

  const navigate = (path: string) => window.location.assign(path)

  /** 在状态提交前同步更新范围引用，让已在途的旧范围请求立刻失效。 */
  const changeRange = (nextRange: DashboardRange) => {
    activeRange.current = nextRange
    setRange(nextRange)
  }

  /** 手动刷新先使当前在途请求失效，再触发新的摘要与图表请求。 */
  const refresh = () => {
    requestEpoch.current += 1
    setRevision((value) => value + 1)
  }

  // 加载期间仍保留范围切换控件，使用户无需等待慢速 30 天请求即可改看 7 天统计。
  const pageHeader = (
    <ConsolePageHeader
      title={t('dashboard.title')}
      description={t('dashboard.description')}
      actions={
        <>
          <ButtonGroup aria-label={t('dashboard.rangeLabel')}>
            <Button theme={range === '7d' ? 'solid' : 'light'} type="primary" onClick={() => changeRange('7d')}>{t('dashboard.sevenDays')}</Button>
            <Button theme={range === '30d' ? 'solid' : 'light'} type="primary" onClick={() => changeRange('30d')}>{t('dashboard.thirtyDays')}</Button>
          </ButtonGroup>
          <Button icon={<IconRefresh />} onClick={refresh}>{t('dashboard.refresh')}</Button>
        </>
      }
    />
  )

  if (failed) return <main>{pageHeader}<RemoteState kind="error" onRetry={load} /></main>
  if (!summary || !analytics) return <main>{pageHeader}<RemoteState kind="loading" /></main>

  return (
    <main>
      {pageHeader}
      <div className="metric-grid console-summary-grid">
        <MetricCard icon={<IconCoinMoney />} tone="blue" label={t('dashboard.balance')} value={formatMetric(summary.availableQuota)} />
        <MetricCard icon={<IconCreditCard />} tone="rose" label={t('dashboard.used')} value={formatMetric(summary.usedQuota)} />
        <MetricCard icon={<IconServer />} tone="mint" label={t('dashboard.requests')} value={formatMetric(summary.requestCount)} />
        <MetricCard
          icon={<IconPulse />}
          tone="amber"
          label={t('dashboard.tokenUsage')}
          value={summary.tokenUsage === null ? t('dashboard.unavailable') : formatMetric(summary.tokenUsage)}
        />
      </div>

      <div className="dashboard-analytics-grid">
        {dailyUsage.length > 0
          ? <DashboardChart title={t('dashboard.trendTitle', { days: range === '7d' ? 7 : 30 })} summary={t('dashboard.trendSummary')} accessibleDescription={trendAccessibleDescription} option={trendOption} />
          : <RemoteState kind="empty" />}
        {topModels.length > 0
          ? <DashboardChart title={t('dashboard.modelsTitle')} summary={t('dashboard.modelsSummary')} accessibleDescription={modelsAccessibleDescription} option={modelOption} />
          : <RemoteState kind="empty" />}
        {tokenUsage.length > 0
          ? <DashboardChart title={t('dashboard.tokensTitle')} summary={t('dashboard.tokensSummary')} accessibleDescription={tokensAccessibleDescription} option={tokenOption} />
          : <RemoteState kind="empty" />}
        <section className="dashboard-actions" aria-label={t('dashboard.quickActions')}>
          <span>{t('dashboard.quickActions')}</span>
          <Button icon={<IconKey />} theme="light" type="primary" onClick={() => navigate('/console/tokens')}>{t('dashboard.createToken')}</Button>
          <Button icon={<IconCoinMoney />} theme="light" type="primary" onClick={() => navigate('/console/recharge')}>{t('dashboard.recharge')}</Button>
          <Button icon={<IconHistory />} theme="light" type="primary" onClick={() => navigate('/console/logs')}>{t('dashboard.viewLogs')}</Button>
          <IconActivity aria-hidden="true" className="dashboard-actions-mark" />
        </section>
      </div>
    </main>
  )
}
