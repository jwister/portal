import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { getTokens, type TokenSummary } from '../../api/portal'

export function TokensPage() {
  const { t } = useTranslation()
  const [tokens, setTokens] = useState<TokenSummary[] | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let active = true
    void getTokens().then((items) => {
      if (active) setTokens(items)
    }).catch(() => {
      if (active) setFailed(true)
    })
    return () => { active = false }
  }, [])

  if (failed) return <main className="console-page"><p className="console-message" role="alert">{t('tokens.error')}</p></main>
  if (!tokens) return <main className="console-page"><p className="console-message">{t('tokens.loading')}</p></main>

  return (
    <main className="console-page">
      <header className="console-heading"><p>ZT / KEYS / 02</p><h1>{t('tokens.title')}</h1></header>
      <section className="token-table-wrap">
        <table>
          <thead><tr><th>{t('tokens.name')}</th><th>{t('tokens.status')}</th><th>{t('tokens.quota')}</th></tr></thead>
          <tbody>{tokens.map((token) => <tr key={token.id}><td>{token.name}</td><td><span className={token.enabled ? 'status status--active' : 'status'}>{token.enabled ? t('tokens.active') : t('tokens.inactive')}</span></td><td>{token.remainingQuota}</td></tr>)}</tbody>
        </table>
      </section>
    </main>
  )
}
