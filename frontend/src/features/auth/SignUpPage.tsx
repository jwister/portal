import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Form, Input, Popover, Toast } from '@douyinfe/semi-ui'

import { AuthApiError, getCaptcha, sendEmailVerification, signUp } from '../../api/auth'
import '../../i18n'

interface SignUpPageProps {
  onRegistered?: () => void
}

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function SignUpPage(props: SignUpPageProps) {
  const { t } = useTranslation()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [captcha, setCaptcha] = useState<{ captchaId: string; image: string } | null>(null)
  const [captchaCode, setCaptchaCode] = useState('')
  const [verificationCode, setVerificationCode] = useState('')
  const [sendingCode, setSendingCode] = useState(false)
  const [countdown, setCountdown] = useState(0)
  const [captchaVisible, setCaptchaVisible] = useState(false)
  const [emailValue, setEmailValue] = useState('')

  const refreshCaptcha = async () => {
    try {
      const next = await getCaptcha()
      setCaptcha(next)
      setCaptchaCode('')
    } catch {
      setError(t('register.captchaLoadError'))
    }
  }
  useEffect(() => {
    if (countdown <= 0) return
    const timer = window.setInterval(() => setCountdown((value) => Math.max(0, value - 1)), 1000)
    return () => window.clearInterval(timer)
  }, [countdown])

  const openCaptcha = async () => {
    if (!emailValue || countdown > 0) return
    setError(null)
    await refreshCaptcha()
    setCaptchaVisible(true)
  }

  const verifyAndSend = async () => {
    if (!captcha || !captchaCode || !emailValue) return
    setSendingCode(true)
    try {
      await sendEmailVerification(emailValue, captcha.captchaId, captchaCode)
      Toast.success(t('register.verificationSent'))
      setCaptchaVisible(false)
      setCountdown(60)
      setCaptchaCode('')
    } catch (cause) {
      const message = cause instanceof AuthApiError ? cause.message : t('register.verificationSendError')
      setError(message)
      Toast.error(message)
      await refreshCaptcha()
    } finally {
      setSendingCode(false)
    }
  }

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
    if (!verificationCode) { setError(t('register.verificationRequired')); return }
    setSubmitting(true)
    setError(null)
    try {
      await signUp(username, email, password, verificationCode)
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
        <Form onSubmit={submit} layout="vertical" onValueChange={(values) => {
          if (typeof values.email === 'string') setEmailValue(values.email)
          if (typeof values.captchaCode === 'string') setCaptchaCode(values.captchaCode)
          if (typeof values.verificationCode === 'string') setVerificationCode(values.verificationCode)
        }}>
          <Form.Input
            field="username"
            label={t('auth.username')}
            rules={[{ required: true, message: t('auth.required') }]}
            autoComplete="username"
          />
          <Form.Input field="email" label={t('auth.email')} rules={[{ required: true, message: t('auth.required') }]} autoComplete="email" />
          <div className="email-code-row"><Form.Input field="verificationCode" label={t('register.verificationCode')} /><Popover visible={captchaVisible} position="bottomRight" trigger="custom" showArrow content={(
            <div className="captcha-modal-content">
              {captcha?.image && <img src={captcha.image} alt={t('register.captchaImage')} />}
              <label className="captcha-modal-label">{t('register.captchaCode')}<Input value={captchaCode} onChange={setCaptchaCode} /></label>
              <div className="captcha-popover-actions"><Button type="tertiary" onClick={() => void refreshCaptcha()}>{t('register.refreshCaptcha')}</Button><Button theme="solid" type="primary" loading={sendingCode} onClick={() => void verifyAndSend()}>{t('register.verifyAndSend')}</Button></div>
              {error && <p className="auth-error" role="alert">{error}</p>}
            </div>
          )}><Button type="tertiary" disabled={countdown > 0 || !emailValue} onClick={() => void openCaptcha()}>{countdown > 0 ? t('register.sendCodeCountdown', { seconds: countdown }) : t('register.sendCode')}</Button></Popover></div>
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
