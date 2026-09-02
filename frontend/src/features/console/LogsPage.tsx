import { Button, Empty, Input, Pagination, Select, Space, Table } from '@douyinfe/semi-ui'
import { IconRefresh } from '@douyinfe/semi-icons'
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

const initialFilters: LogFilters = { modelName: '', tokenName: '', type: '', start: '', end: '' }

function toTimestamp(value: string): number | undefined {
  if (!value) return undefined
  const timestamp = Date.parse(value)
  return Number.isNaN(timestamp) ? undefined : Math.floor(timestamp / 1000)
}

function formatTimestamp(timestamp: number): string {
  return new Date(timestamp * 1000).toLocaleString()
}

export function LogsPage() {
  const { t } = useTranslation()
  const [filters, setFilters] = useState<LogFilters>(initialFilters)
  const [query, setQuery] = useState<LogQuery>({ page: 1, pageSize: 50 })
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
    setFilters(initialFilters)
    setQuery({ page: 1, pageSize: 50 })
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
    { title: t('logs.model'), dataIndex: 'modelName' },
    { title: t('logs.token'), dataIndex: 'tokenName' },
    { title: t('logs.quota'), dataIndex: 'quota' },
    { title: t('logs.content'), dataIndex: 'content' },
    { title: t('logs.request'), dataIndex: 'requestId' },
  ]

  return (
    <main>
      <ConsolePageHeader title={t('logs.title')} description={t('logs.description')} actions={<Button icon={<IconRefresh />} onClick={() => setQuery({ ...query })}>{t('dashboard.refresh')}</Button>} />
      <section className="console-filter-bar" aria-label={t('logs.title')}>
        <label>{t('logs.model')}<Input value={filters.modelName} onChange={(value) => setFilters((current) => ({ ...current, modelName: value }))} /></label>
        <label>{t('logs.token')}<Input value={filters.tokenName} onChange={(value) => setFilters((current) => ({ ...current, tokenName: value }))} /></label>
        <label>{t('logs.type')}<Select value={filters.type || undefined} onChange={(value) => setFilters((current) => ({ ...current, type: String(value ?? '') }))} optionList={[
          { label: t('logs.allTypes'), value: '' },
          { label: t('logs.consume'), value: '2' },
          { label: t('logs.errorType'), value: '5' },
        ]} /></label>
        <label>{t('logs.start')}<Input type="datetime-local" value={filters.start} onChange={(value) => setFilters((current) => ({ ...current, start: value }))} /></label>
        <label>{t('logs.end')}<Input type="datetime-local" value={filters.end} onChange={(value) => setFilters((current) => ({ ...current, end: value }))} /></label>
        <Space><Button theme="solid" type="primary" onClick={applyFilters}>{t('logs.apply')}</Button><Button onClick={clearFilters}>{t('logs.clear')}</Button></Space>
      </section>
      <Space className="metric-grid" spacing="tight" wrap>
        <MetricCard label={t('logs.quota')} value={stats.quota.toLocaleString()} />
        <MetricCard label={t('logs.rpm')} value={stats.rpm.toLocaleString()} />
        <MetricCard label={t('logs.tpm')} value={stats.tpm.toLocaleString()} />
      </Space>
      {logs.items.length === 0
        ? <Empty description={t('logs.empty')} />
        : <div className="console-table-wrap"><Table columns={columns} dataSource={logs.items} rowKey="id" pagination={false} /></div>}
      {logs.total > logs.pageSize && <Pagination currentPage={logs.page} pageSize={logs.pageSize} total={logs.total} onPageChange={(page) => setQuery({ ...query, page })} />}
    </main>
  )
}
