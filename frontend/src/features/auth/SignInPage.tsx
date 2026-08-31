import { FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'

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
          <label>{t('auth.username')}<input value={username} onChange={(event) => setUsername(event.target.value)} required autoComplete="username" /></label>
          <label>{t('auth.password')}<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required autoComplete="current-password" /></label>
          {failed && <p className="auth-error" role="alert">{t('auth.error')}</p>}
          <button className="primary-action" type="submit" disabled={submitting}>{t('auth.submit')}</button>
        </form>
      </section>
    </main>
  )
}
