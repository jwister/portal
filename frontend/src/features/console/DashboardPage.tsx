import { Button, Space } from '@douyinfe/semi-ui'
import { IconRefresh } from '@douyinfe/semi-icons'
import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { ConsolePageHeader } from '../../components/ConsolePageHeader'
import { MetricCard } from '../../components/MetricCard'
import { RemoteState } from '../../components/RemoteState'
import { getDashboard, type DashboardSummary } from '../../api/portal'

function formatMetric(value: number): string {
  return new Intl.NumberFormat().format(value)
}

export function DashboardPage() {
  const { t } = useTranslation()
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [failed, setFailed] = useState(false)

  const load = useCallback(() => {
    setFailed(false)
    setSummary(null)
    void getDashboard()
      .then(setSummary)
      .catch(() => setFailed(true))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  if (failed) return <RemoteState kind="error" onRetry={load} />
  if (!summary) return <RemoteState kind="loading" />

  return (
    <main>
      <ConsolePageHeader
        title={t('dashboard.title')}
        description={t('dashboard.description')}
        actions={<Button icon={<IconRefresh />} onClick={load}>{t('dashboard.refresh')}</Button>}
      />
      <Space className="metric-grid" spacing="tight" wrap>
        <MetricCard label={t('dashboard.balance')} value={formatMetric(summary.availableQuota)} />
        <MetricCard label={t('dashboard.used')} value={formatMetric(summary.usedQuota)} />
        <MetricCard label={t('dashboard.requests')} value={formatMetric(summary.requestCount)} />
        <MetricCard
          label={t('dashboard.tokenUsage')}
          value={summary.tokenUsage === null ? t('dashboard.unavailable') : formatMetric(summary.tokenUsage)}
        />
      </Space>
    </main>
  )
}
