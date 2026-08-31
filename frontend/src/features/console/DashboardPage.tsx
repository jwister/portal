import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { getDashboard, type DashboardSummary } from '../../api/portal'

export function DashboardPage() {
  const { t } = useTranslation()
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let active = true
    void getDashboard()
      .then((data) => {
        if (active) setSummary(data)
      })
      .catch(() => {
        if (active) setFailed(true)
      })
    return () => {
      active = false
    }
  }, [])

  if (failed) {
    return <main className="console-page"><p className="console-message" role="alert">{t('dashboard.error')}</p></main>
  }

  if (!summary) {
    return <main className="console-page"><p className="console-message">{t('dashboard.loading')}</p></main>
  }

  return (
    <main className="console-page">
      <header className="console-heading">
        <p>ZT / CONSOLE / 01</p>
        <h1>{t('dashboard.title')}</h1>
      </header>
      <section className="instrument-grid" aria-label={t('dashboard.title')}>
        <article className="instrument instrument--primary">
          <span>{t('dashboard.balance')}</span>
          <strong>{summary.availableQuota}</strong>
          <i aria-hidden="true" />
        </article>
        <article className="instrument">
          <span>{t('dashboard.used')}</span>
          <strong>{summary.usedQuota}</strong>
        </article>
        <article className="instrument">
          <span>{t('dashboard.requests')}</span>
          <strong>{summary.requestCount}</strong>
        </article>
      </section>
    </main>
  )
}
