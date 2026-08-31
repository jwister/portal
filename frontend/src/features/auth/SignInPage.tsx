import { FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Input, Toast } from '@douyinfe/semi-ui'

import '../../i18n'

interface SignInPageProps {
  onAuthenticated?: () => void
}

export function SignInPage(props: SignInPageProps) {
  const { t } = useTranslation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [failed, setFailed] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault()
    setSubmitting(true)
    setFailed(false)
    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      })
      if (!response.ok) throw new Error('Sign-in failed')
      props.onAuthenticated?.()
    } catch {
      setFailed(true)
      Toast.error(t('auth.error'))
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
        <form onSubmit={submit}>
          <label>{t('auth.username')}<Input value={username} onChange={setUsername} required autoComplete="username" /></label>
          <label>{t('auth.password')}<Input type="password" value={password} onChange={setPassword} required autoComplete="current-password" /></label>
          {failed && <p className="auth-error" role="alert">{t('auth.error')}</p>}
          <Button type="primary" theme="solid" htmlType="submit" loading={submitting}>{t('auth.submit')}</Button>
          <p className="auth-switch">还没有账户？ <a href="/sign-up">{t('register.submit')}</a></p>
        </form>
      </section>
    </main>
  )
}
