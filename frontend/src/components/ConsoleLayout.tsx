import { Avatar, Button, Layout, Nav, Space, Toast } from '@douyinfe/semi-ui'
import { IconBell, IconCreditCard, IconExit, IconHistory, IconHome, IconKey, IconUser, IconList } from '@douyinfe/semi-icons'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { signOut } from '../api/auth'
import { useAuthStatus } from '../auth/use-auth-status'
import '../i18n'

export type ConsoleKey = 'dashboard' | 'recharge' | 'tokens' | 'logs' | 'profile' | 'orders'

interface ConsoleLayoutProps {
  activeKey: ConsoleKey
  children: ReactNode
  onNavigate?: (path: string) => void
}

const destinations: Record<ConsoleKey, string> = {
  dashboard: '/console/dashboard',
  recharge: '/console/recharge',
  tokens: '/console/tokens',
  logs: '/console/logs',
  profile: '/console/profile',
  orders: '/console/orders',
}

export function ConsoleLayout(props: ConsoleLayoutProps) {
  const { t } = useTranslation()
  const status = useAuthStatus()
  const accountName = status.kind === 'authenticated' ? status.profile.username : t('console.account')
  const labels: Record<ConsoleKey, string> = { dashboard: t('console.dashboard'), recharge: t('console.recharge'), tokens: t('console.tokens'), logs: t('console.logs'), profile: t('console.profile'), orders: t('console.orders') }
  const handleSignOut = async () => {
    try {
      await signOut()
      window.location.assign('/')
    } catch {
      Toast.error(t('auth.signOutError'))
    }
  }
  return (
    <Layout className="console-shell">
      <Layout.Sider className="console-sider">
        <a className="console-brand" href="/"><img src="/small-logo.png" alt="" /><strong>{t('brand.name')}</strong></a>
        <nav aria-label={t('console.navigation')}><Nav mode="vertical" selectedKeys={[props.activeKey]} onSelect={({ itemKey }) => {
          const path = destinations[itemKey as ConsoleKey]
          if (props.onNavigate) props.onNavigate(path)
          else window.location.assign(path)
        }} items={[
          { itemKey: 'dashboard', text: labels.dashboard, icon: <IconHome /> },
          { itemKey: 'recharge', text: labels.recharge, icon: <IconCreditCard /> },
          { itemKey: 'tokens', text: labels.tokens, icon: <IconKey /> },
          { itemKey: 'logs', text: labels.logs, icon: <IconHistory /> },
          { itemKey: 'profile', text: labels.profile, icon: <IconUser /> },
          { itemKey: 'orders', text: labels.orders, icon: <IconList /> },
        ]} /></nav>
      </Layout.Sider>
      <Layout>
        <Layout.Header className="console-topbar"><h1>{labels[props.activeKey]}</h1><Space spacing="tight"><Button theme="borderless" icon={<IconBell />} aria-label={t('console.notifications')} /><div className="console-account public-account"><Avatar size="small" className="public-avatar" aria-label={t('auth.avatarLabel', { username: accountName })} tabIndex={0}>{accountName.charAt(0).toUpperCase()}</Avatar><div className="public-account-menu"><span className="public-username"><IconUser aria-hidden="true" />{accountName}</span><Button theme="borderless" className="public-logout" icon={<IconExit aria-hidden="true" />} onClick={() => void handleSignOut()}>{t('auth.signOut')}</Button></div></div></Space></Layout.Header>
        <Layout.Content className="console-content">{props.children}</Layout.Content>
      </Layout>
    </Layout>
  )
}
