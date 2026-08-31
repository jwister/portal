import { useTranslation } from 'react-i18next'

import { setStoredLanguage, type PortalLanguage } from './i18n'
import { DashboardPage } from './features/console/DashboardPage'
import { SignInPage } from './features/auth/SignInPage'
import { SignUpPage } from './features/auth/SignUpPage'

export function App() {
  const { i18n, t } = useTranslation()
  const nextLanguage: PortalLanguage = i18n.language === 'zh-CN' ? 'en' : 'zh-CN'

  if (window.location.pathname === '/console/dashboard') {
    return <DashboardPage />
  }

  if (window.location.pathname === '/sign-in') {
    return <SignInPage onAuthenticated={() => window.location.assign('/console/dashboard')} />
  }

  if (window.location.pathname === '/sign-up') {
    return <SignUpPage onRegistered={() => window.location.assign('/sign-in')} />
  }

  const changeLanguage = async (): Promise<void> => {
    setStoredLanguage(nextLanguage)
    await i18n.changeLanguage(nextLanguage)
  }

  return (
    <main className="site-shell">
      <header className="site-header">
        <a className="brand" href="/" aria-label={t('brand.name')}>
          <span className="brand-mark" aria-hidden="true">Z</span>
          <span>{t('brand.name')}</span>
        </a>
        <nav aria-label="Primary navigation">
          <a href="/models">{t('nav.models')}</a>
          <a href="https://docs.newapi.pro/zh/docs/api" target="_blank" rel="noreferrer">{t('nav.docs')}</a>
          <a href="/purchase">{t('nav.purchase')}</a>
        </nav>
        <div className="header-actions">
          <button className="language-switch" type="button" onClick={changeLanguage}>
            {nextLanguage === 'zh-CN' ? '中文' : 'EN'}
          </button>
          <a className="console-link" href="/console/dashboard">{t('nav.console')}</a>
        </div>
      </header>

      <section className="hero" aria-labelledby="hero-title">
        <div className="hero-copy">
          <p className="eyebrow">{t('hero.eyebrow')}</p>
          <h1 id="hero-title">{t('hero.title')}</h1>
          <p className="hero-description">{t('hero.copy')}</p>
          <div className="hero-actions">
            <a className="primary-action" href="/console/dashboard">{t('hero.primary')}</a>
            <a className="secondary-action" href="/models">{t('hero.secondary')} <span aria-hidden="true">↗</span></a>
          </div>
        </div>
        <aside className="signal-panel" aria-label="Platform signal">
          <div className="signal-line"><span>01</span><i /><b>UP</b></div>
          <div className="signal-line"><span>02</span><i /><b>READY</b></div>
          <div className="signal-line"><span>03</span><i /><b>DIRECT</b></div>
          <p>ZT / 01</p>
        </aside>
      </section>

      <section className="metric-strip" aria-label="Platform overview">
        <div><strong>40+</strong><span>{t('hero.metric.models')}</span></div>
        <div><strong>/v1</strong><span>{t('hero.metric.access')}</span></div>
        <div><strong>2</strong><span>{t('hero.metric.language')}</span></div>
      </section>
    </main>
  )
}
