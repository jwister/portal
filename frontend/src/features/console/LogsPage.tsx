import { Button, Empty, Input, Pagination, Select, Space, Table } from '@douyinfe/semi-ui'
import { IconCalendar, IconCreditCard, IconFilter, IconHistogram, IconPulse, IconRefresh, IconSearch } from '@douyinfe/semi-icons'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { ConsolePageHeader } from '../../components/ConsolePageHeader'
import { MetricCard } from '../../components/MetricCard'
import { RemoteState } from '../../components/RemoteState'
import { getLogs, getLogStats, type LogPage, type LogQuery, type LogStats } from '../../api/portal'

interface LogFilters {
  modelName: string
  tokenName: string
  type: string
  start: string
  end: string
}

function localDateTime(value: Date): string {
  const pad = (number: number) => String(number).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}`
}
function todayFilters(): LogFilters { const end = new Date(); const start = new Date(end); start.setHours(0, 0, 0, 0); return { modelName: '', tokenName: '', type: '', start: localDateTime(start), end: localDateTime(end) } }
const initialFilters: LogFilters = todayFilters()

function toTimestamp(value: string): number | undefined {
  if (!value) return undefined
  const timestamp = Date.parse(value)
  return Number.isNaN(timestamp) ? undefined : Math.floor(timestamp / 1000)
}

function formatTimestamp(timestamp: number): string {
  return new Date(timestamp * 1000).toLocaleString()
}

const QUOTA_PER_USD = 500_000
const formatUsd = (quota: number) => `$${(quota / QUOTA_PER_USD).toFixed(4)}`
const formatSeconds = (seconds: number) => `${seconds.toFixed(1)}s`

export function LogsPage() {
  const { t } = useTranslation()
  const [filters, setFilters] = useState<LogFilters>(initialFilters)
  const [query, setQuery] = useState<LogQuery>(() => ({ page: 1, pageSize: 50, startTimestamp: toTimestamp(initialFilters.start), endTimestamp: toTimestamp(initialFilters.end) }))
  const [logs, setLogs] = useState<LogPage | null>(null)
  const [stats, setStats] = useState<LogStats | null>(null)
  const [failed, setFailed] = useState(false)

  const applyFilters = () => {
    setQuery({
      page: 1,
      pageSize: 50,
      modelName: filters.modelName.trim() || undefined,
      tokenName: filters.tokenName.trim() || undefined,
      type: filters.type ? Number(filters.type) : undefined,
      startTimestamp: toTimestamp(filters.start),
      endTimestamp: toTimestamp(filters.end),
    })
  }

  const clearFilters = () => {
    const next = todayFilters()
    setFilters(next)
    setQuery({ page: 1, pageSize: 50, startTimestamp: toTimestamp(next.start), endTimestamp: toTimestamp(next.end) })
  }

  useEffect(() => {
    let active = true
    setFailed(false)
    setLogs(null)
    setStats(null)
    void Promise.all([
      getLogs(query),
      getLogStats({
        modelName: query.modelName,
        tokenName: query.tokenName,
        type: query.type,
        startTimestamp: query.startTimestamp,
        endTimestamp: query.endTimestamp,
      }),
    ]).then(([nextLogs, nextStats]) => {
      if (!active) return
      setLogs(nextLogs)
      setStats(nextStats)
    }).catch(() => {
      if (active) setFailed(true)
    })
    return () => { active = false }
  }, [query])

  if (failed) return <RemoteState kind="error" onRetry={() => setQuery({ ...query })} />
  if (!logs || !stats) return <RemoteState kind="loading" />

  const columns = [
    { title: t('logs.time'), dataIndex: 'createdAt', render: (value: number) => formatTimestamp(value) },
    { title: t('logs.token'), dataIndex: 'tokenName' },
    { title: t('logs.model'), dataIndex: 'modelName' },
    { title: t('logs.stream'), render: (_: unknown, log: LogPage['items'][number]) => log.stream && log.useTime > 0 ? `${(log.completionTokens / log.useTime).toFixed(1)} t/s` : '-' },
    { title: t('logs.tokens'), render: (_: unknown, log: LogPage['items'][number]) => <span>{t('logs.inputTokens')} {log.promptTokens} / {t('logs.outputTokens')} {log.completionTokens}<br />{t('logs.cacheRead')} {log.cacheTokens} / {t('logs.cacheWrite')} {log.cacheCreationTokens}</span> },
    { title: t('logs.cost'), dataIndex: 'quota', render: (value: number) => formatUsd(value) },
    { title: t('logs.duration'), render: (_: unknown, log: LogPage['items'][number]) => <span>{t('logs.firstToken')} {log.firstResponseTime > 0 ? formatSeconds(log.firstResponseTime / 1000) : '-'}<br />{t('logs.totalTime')} {formatSeconds(log.useTime)}</span> },
    { title: t('logs.content'), dataIndex: 'content', width: '10%', render: (value: string) => <span className="log-detail" title={value}>{value}</span> },
  ]

  return (
    <main>
      <ConsolePageHeader title={t('logs.title')} description={t('logs.description')} actions={<Button icon={<IconRefresh />} onClick={() => setQuery({ ...query })}>{t('dashboard.refresh')}</Button>} />
      <section className="console-filter-bar console-table-toolbar" aria-label={t('logs.filters')}>
        <label>{t('logs.model')}<Input prefix={<IconSearch />} value={filters.modelName} onChange={(value) => setFilters((current) => ({ ...current, modelName: value }))} /></label>
        <label>{t('logs.token')}<Input prefix={<IconSearch />} value={filters.tokenName} onChange={(value) => setFilters((current) => ({ ...current, tokenName: value }))} /></label>
        <label>{t('logs.type')}<Select prefix={<IconFilter />} value={filters.type || undefined} onChange={(value) => setFilters((current) => ({ ...current, type: String(value ?? '') }))} optionList={[
          { label: t('logs.allTypes'), value: '' },
          { label: t('logs.consume'), value: '2' },
          { label: t('logs.errorType'), value: '5' },
        ]} /></label>
        <label>{t('logs.start')}<Input prefix={<IconCalendar />} type="datetime-local" value={filters.start} onChange={(value) => setFilters((current) => ({ ...current, start: value }))} /></label>
        <label>{t('logs.end')}<Input prefix={<IconCalendar />} type="datetime-local" value={filters.end} onChange={(value) => setFilters((current) => ({ ...current, end: value }))} /></label>
        <Space><Button theme="solid" type="primary" onClick={applyFilters}>{t('logs.apply')}</Button><Button onClick={clearFilters}>{t('logs.clear')}</Button></Space>
      </section>
      <section className="console-summary-grid logs-summary-grid" aria-label={t('logs.title')}>
        <MetricCard label={t('logs.quota')} value={formatUsd(stats.quota)} icon={<IconCreditCard />} tone="blue" />
        <MetricCard label={t('logs.rpm')} value={stats.rpm.toLocaleString()} icon={<IconPulse />} tone="mint" />
        <MetricCard label={t('logs.tpm')} value={stats.tpm.toLocaleString()} icon={<IconHistogram />} tone="amber" />
      </section>
      {logs.items.length === 0
        ? <Empty description={t('logs.empty')} />
        : <div className="console-table-wrap logs-table-wrap"><Table columns={columns} dataSource={logs.items} rowKey="id" pagination={false} /></div>}
      {logs.total > logs.pageSize && <Pagination currentPage={logs.page} pageSize={logs.pageSize} total={logs.total} onPageChange={(page) => setQuery({ ...query, page })} />}
    </main>
  )
}
