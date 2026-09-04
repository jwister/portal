import { Avatar, Button, Nav, Space, Toast } from '@douyinfe/semi-ui'
import { useTranslation } from 'react-i18next'

import { useAuthStatus } from '../auth/use-auth-status'
import { signOut } from '../api/auth'
import i18n, { setStoredLanguage } from '../i18n'

function navigate(path: string): void { window.location.assign(path) }

export function PublicHeader() {
  const { t } = useTranslation()
  const status = useAuthStatus()
  const nextLanguage = i18n.language.startsWith('zh') ? 'en' : 'zh-CN'
  const navItems = [
    { itemKey: '/', text: <a href="/">{t('nav.home')}</a> },
    { itemKey: '/models', text: <a href="/models">{t('nav.models')}</a> },
    { itemKey: 'https://docs.newapi.pro/zh/docs/api', text: <a href="https://docs.newapi.pro/zh/docs/api">{t('nav.docs')}</a> },
    { itemKey: '/purchase', text: <a href="/purchase">{t('nav.purchase')}</a> },
  ]
  const handleSignOut = async () => {
    try {
      await signOut()
      window.location.reload()
    } catch {
      Toast.error(t('auth.signOutError'))
    }
  }
  const account = status.kind === 'authenticated' ? status.profile : null
  const footer = status.kind === 'authenticated'
    ? <Space spacing="tight" className="public-header-actions"><Button theme="borderless" aria-label={nextLanguage === 'zh-CN' ? '中文' : 'English'} onClick={() => setStoredLanguage(nextLanguage)}>{nextLanguage === 'zh-CN' ? '中文' : 'EN'}</Button><Button theme="solid" type="primary" onClick={() => navigate('/console/dashboard')}>{t('nav.console')}</Button><div className="public-account"><Avatar size="small" className="public-avatar" aria-label={t('auth.avatarLabel', { username: account?.username ?? '' })} tabIndex={0}>{account?.username.charAt(0).toUpperCase()}</Avatar><div className="public-account-menu"><span className="public-username">{account?.username}</span><Button theme="borderless" className="public-logout" onClick={() => void handleSignOut()}>{t('auth.signOut')}</Button></div></div></Space>
    : <Space spacing="tight" className="public-header-actions"><Button theme="borderless" aria-label={nextLanguage === 'zh-CN' ? '中文' : 'English'} onClick={() => setStoredLanguage(nextLanguage)}>{nextLanguage === 'zh-CN' ? '中文' : 'EN'}</Button><Button theme="solid" type="primary" loading={status.kind === 'loading'} onClick={() => navigate('/sign-in')}>{t('auth.submit')}</Button></Space>
  return <header className="public-header"><Nav mode="horizontal" className="public-nav" header={<a className="semi-brand" href="/" aria-label="Ztoken"><img src="/small-logo.png" alt="" />Ztoken</a>} items={navItems}
    onSelect={({ itemKey }) => { const path = String(itemKey); if (path.startsWith('http')) window.open(path, '_blank', 'noopener,noreferrer'); else navigate(path) }}
    footer={footer} /></header>
}
