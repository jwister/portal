import { useEffect, useRef, useState } from 'react'
import { Button } from '@douyinfe/semi-ui'
import { useTranslation } from 'react-i18next'

import { completeOAuth } from '../../api/auth'
import '../../i18n'

interface OAuthCallbackPageProps {
  provider: 'github' | 'oidc'
  onAuthenticated?: () => void
}

/**
 * 接收授权服务的短期回调参数并交给 Portal BFF；页面不展示或持久化其中的敏感值。
 */
export function OAuthCallbackPage({ provider, onAuthenticated }: OAuthCallbackPageProps) {
  const { t } = useTranslation()
  const started = useRef(false)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    if (started.current) return
    started.current = true
    const query = new URLSearchParams(window.location.search)
    const code = query.get('code')
    const state = query.get('state')
    const error = query.get('error')
    const errorDescription = query.get('error_description')
    if (!state || (!code && !error)) {
      setFailed(true)
      return
    }
    void completeOAuth(provider, { code, state, error, errorDescription })
      .then(() => {
        if (onAuthenticated) onAuthenticated()
        else window.location.replace('/console/dashboard')
      })
      .catch(() => setFailed(true))
  }, [onAuthenticated, provider])

  return (
    <main className="auth-page ledger-auth-page">
      <section className="auth-panel auth-callback-panel" aria-live="polite">
        <a className="brand" href="/" aria-label="Ztoken"><span className="brand-mark" aria-hidden="true">Z</span><span>Ztoken</span></a>
        {failed ? <>
          <h1>{t('auth.oauthFailed')}</h1>
          <p role="alert">{t('auth.oauthFailed')}</p>
          <Button type="primary" theme="solid" htmlType="button" onClick={() => window.location.replace('/sign-in')}>{t('auth.submit')}</Button>
        </> : <>
          <h1>{t('auth.oauthLoading')}</h1>
          <p>{t('auth.oauthLoading')}</p>
        </>}
      </section>
    </main>
  )
}
