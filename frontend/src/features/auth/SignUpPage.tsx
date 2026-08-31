import { FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Input, Toast } from '@douyinfe/semi-ui'

import '../../i18n'

interface SignUpPageProps {
  onRegistered?: () => void
}

export function SignUpPage(props: SignUpPageProps) {
  const { t } = useTranslation()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [failed, setFailed] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault()
    setSubmitting(true)
    setFailed(false)
    try {
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, email, password }),
      })
      if (!response.ok) throw new Error('Registration failed')
      if (props.onRegistered) {
        props.onRegistered()
      } else {
        window.location.assign('/sign-in')
      }
    } catch {
      setFailed(true)
      Toast.error(t('register.error'))
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
        <form onSubmit={submit}>
          <label>{t('auth.username')}<Input value={username} onChange={setUsername} required autoComplete="username" /></label>
          <label>{t('auth.email')}<Input type="email" value={email} onChange={setEmail} required autoComplete="email" /></label>
          <label>{t('auth.password')}<Input type="password" value={password} onChange={setPassword} required autoComplete="new-password" /></label>
          {failed && <p className="auth-error" role="alert">{t('register.error')}</p>}
          <Button type="primary" theme="solid" htmlType="submit" loading={submitting}>{t('register.submit')}</Button>
          <p className="auth-switch">已有账户？ <a href="/sign-in">{t('auth.submit')}</a></p>
        </form>
      </section>
    </main>
  )
}
