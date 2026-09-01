import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Form, Toast } from '@douyinfe/semi-ui'

import { AuthApiError, signUp } from '../../api/auth'
import '../../i18n'

interface SignUpPageProps {
  onRegistered?: () => void
}

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function SignUpPage(props: SignUpPageProps) {
  const { t } = useTranslation()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (values: Record<string, unknown>): Promise<void> => {
    const username = typeof values.username === 'string' ? values.username.trim() : ''
    const email = typeof values.email === 'string' ? values.email.trim() : ''
    const password = typeof values.password === 'string' ? values.password : ''
    const confirmPassword = typeof values.confirmPassword === 'string' ? values.confirmPassword : ''
    if (!emailPattern.test(email)) {
      setError(t('auth.emailInvalid'))
      return
    }
    if (password !== confirmPassword) {
      setError(t('register.passwordMismatch'))
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await signUp(username, email, password)
      if (props.onRegistered) {
        props.onRegistered()
      } else {
        window.location.assign('/sign-in')
      }
    } catch (cause) {
      const message = cause instanceof AuthApiError ? cause.message : t('register.error')
      setError(message)
      Toast.error(message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel" aria-labelledby="sign-up-title">
        <a className="brand" href="/" aria-label="Ztoken"><span className="brand-mark" aria-hidden="true">Z</span><span>Ztoken</span></a>
        <p className="eyebrow">ZT / CREATE</p>
        <h1 id="sign-up-title">{t('register.title')}</h1>
        <p>{t('register.copy')}</p>
        <Form onSubmit={submit} layout="vertical">
          <Form.Input
            field="username"
            label={t('auth.username')}
            rules={[{ required: true, message: t('auth.required') }]}
            autoComplete="username"
          />
          <Form.Input
            field="email"
            label={t('auth.email')}
            rules={[{ required: true, message: t('auth.required') }]}
            autoComplete="email"
          />
          <Form.Input
            field="password"
            label={t('auth.password')}
            mode="password"
            rules={[{ required: true, message: t('auth.required') }]}
            autoComplete="new-password"
          />
          <Form.Input
            field="confirmPassword"
            label={t('register.confirmPassword')}
            mode="password"
            rules={[{ required: true, message: t('auth.required') }]}
            autoComplete="new-password"
          />
          {error && <p className="auth-error" role="alert">{error}</p>}
          <Button type="primary" theme="solid" htmlType="submit" loading={submitting}>{t('register.submit')}</Button>
          <p className="auth-switch">{t('register.haveAccount')} <a href="/sign-in">{t('auth.submit')}</a></p>
        </Form>
      </section>
    </main>
  )
}