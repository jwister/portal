import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Form, Toast } from '@douyinfe/semi-ui'

import { AuthApiError, getSafeReturnTo, signIn } from '../../api/auth'
import '../../i18n'

interface SignInPageProps {
  onAuthenticated?: () => void
}

export function SignInPage(props: SignInPageProps) {
  const { t } = useTranslation()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

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

  return (
    <main className="auth-page">
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
          <p className="auth-switch">{t('auth.noAccount')} <a href="/sign-up">{t('register.submit')}</a></p>
        </Form>
      </section>
    </main>
  )
}
