import { FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'

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
          <label>{t('auth.username')}<input value={username} onChange={(event) => setUsername(event.target.value)} required autoComplete="username" /></label>
          <label>{t('auth.email')}<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required autoComplete="email" /></label>
          <label>{t('auth.password')}<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required autoComplete="new-password" /></label>
          {failed && <p className="auth-error" role="alert">{t('register.error')}</p>}
          <button className="primary-action" type="submit" disabled={submitting}>{t('register.submit')}</button>
        </form>
      </section>
    </main>
  )
}
