import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Form, Toast } from '@douyinfe/semi-ui'

import {
  AuthApiError,
  buildGitHubOAuthUrl,
  buildOidcOAuthUrl,
  createOAuthState,
  getOAuthProviders,
  getSafeReturnTo,
  signIn,
  type OAuthProviderStatus,
} from '../../api/auth'
import '../../i18n'

interface SignInPageProps {
  onAuthenticated?: () => void
  onOAuthNavigate?: (url: string) => void
}

export function SignInPage(props: SignInPageProps) {
  const { t } = useTranslation()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [providers, setProviders] = useState<OAuthProviderStatus | null>(null)
  const [oauthProvider, setOAuthProvider] = useState<'github' | 'oidc' | null>(null)

  useEffect(() => {
    let active = true
    void getOAuthProviders().then((status) => {
      if (active) setProviders(status)
    }).catch(() => {
      // OAuth 未配置或临时不可用时保留密码登录，不向用户展示上游错误。
      if (active) setProviders(null)
    })
    return () => { active = false }
  }, [])

  const submit = async (values: Record<string, unknown>): Promise<void> => {
    const username = typeof values.username === 'string' ? values.username.trim() : ''
    const password = typeof values.password === 'string' ? values.password : ''
    setSubmitting(true)
    setError(null)
    try {
      await signIn(username, password)
      if (props.onAuthenticated) props.onAuthenticated()
      else window.location.assign(getSafeReturnTo(new URLSearchParams(window.location.search).get('returnTo')))
    } catch (cause) {
      const message = cause instanceof AuthApiError ? cause.message : t('auth.error')
      setError(message)
      Toast.error(message)
    } finally {
      setSubmitting(false)
    }
  }

  const startOAuth = async (provider: 'github' | 'oidc'): Promise<void> => {
    if (!providers || oauthProvider) return
    setOAuthProvider(provider)
    setError(null)
    try {
      const state = await createOAuthState(provider)
      const destination = provider === 'github'
        ? buildGitHubOAuthUrl(providers.githubClientId, state)
        : buildOidcOAuthUrl(providers.oidcAuthorizationEndpoint, providers.oidcClientId, state)
      if (props.onOAuthNavigate) props.onOAuthNavigate(destination)
      else window.location.assign(destination)
    } catch {
      const message = t('auth.oauthFailed')
      setError(message)
      Toast.error(message)
      setOAuthProvider(null)
    }
  }

  const githubEnabled = !!providers?.githubEnabled && !!providers.githubClientId
  const oidcEnabled = !!providers?.oidcEnabled && !!providers.oidcClientId && !!providers.oidcAuthorizationEndpoint

  return (
    <main className="auth-page ledger-auth-page" data-testid="ledger-auth-page">
      <section className="auth-panel" aria-labelledby="sign-in-title">
        <a className="brand" href="/" aria-label="Ztoken"><span className="brand-mark" aria-hidden="true">Z</span><span>Ztoken</span></a>
        <p className="eyebrow">ZT / ACCESS</p>
        <h1 id="sign-in-title">{t('auth.title')}</h1>
        <p>{t('auth.copy')}</p>
        <Form onSubmit={submit} layout="vertical">
          <Form.Input
            field="username"
            label={t('auth.username')}
            placeholder={t('auth.username')}
            rules={[{ required: true, message: t('auth.required') }]}
            autoComplete="username"
          />
          <Form.Input
            field="password"
            label={t('auth.password')}
            mode="password"
            rules={[{ required: true, message: t('auth.required') }]}
            autoComplete="current-password"
          />
          {error && <p className="auth-error" role="alert">{error}</p>}
          <Button type="primary" theme="solid" htmlType="submit" loading={submitting}>{t('auth.submit')}</Button>
          {(githubEnabled || oidcEnabled) && <div className="auth-oauth" aria-label={t('auth.oauthDivider')}>
            <span>{t('auth.oauthDivider')}</span>
            {githubEnabled && <Button
              type="tertiary"
              theme="borderless"
              htmlType="button"
              className="auth-oauth-button"
              loading={oauthProvider === 'github'}
              disabled={!!oauthProvider}
              onClick={() => void startOAuth('github')}
            >{t('auth.continueGithub')}</Button>}
            {oidcEnabled && <Button
              type="tertiary"
              theme="borderless"
              htmlType="button"
              className="auth-oauth-button"
              loading={oauthProvider === 'oidc'}
              disabled={!!oauthProvider}
              onClick={() => void startOAuth('oidc')}
            >{t('auth.continueGoogle', { provider: providers?.oidcDisplayName || 'Google' })}</Button>}
          </div>}
          <p className="auth-switch">{t('auth.noAccount')} <a href="/sign-up">{t('register.submit')}</a></p>
        </Form>
      </section>
    </main>
  )
}
