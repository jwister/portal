import { Avatar, Button, Layout, Nav, Space } from '@douyinfe/semi-ui'
import { IconBell, IconCreditCard, IconHistory, IconHome, IconKey, IconUser, IconList } from '@douyinfe/semi-icons'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import '../i18n'

export type ConsoleKey = 'dashboard' | 'recharge' | 'tokens' | 'logs' | 'profile' | 'orders'

interface ConsoleLayoutProps {
  activeKey: ConsoleKey
  children: ReactNode
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
  const labels: Record<ConsoleKey, string> = { dashboard: t('console.dashboard'), recharge: t('console.recharge'), tokens: t('console.tokens'), logs: t('console.logs'), profile: t('console.profile'), orders: t('console.orders') }
  return (
    <Layout className="console-shell">
      <Layout.Sider className="console-sider">
        <div className="console-brand"><span>Z</span><strong>{t('brand.name')}</strong></div>
        <nav aria-label={t('console.navigation')}><Nav mode="vertical" selectedKeys={[props.activeKey]} onSelect={({ itemKey }) => window.location.assign(destinations[itemKey as ConsoleKey])} items={[
          { itemKey: 'dashboard', text: labels.dashboard, icon: <IconHome /> },
          { itemKey: 'recharge', text: labels.recharge, icon: <IconCreditCard /> },
          { itemKey: 'tokens', text: labels.tokens, icon: <IconKey /> },
          { itemKey: 'logs', text: labels.logs, icon: <IconHistory /> },
          { itemKey: 'profile', text: labels.profile, icon: <IconUser /> },
          { itemKey: 'orders', text: labels.orders, icon: <IconList /> },
        ]} /></nav>
      </Layout.Sider>
      <Layout>
        <Layout.Header className="console-topbar"><h1>{labels[props.activeKey]}</h1><Space spacing="tight"><a className="console-home-link" href="/" aria-label={t('console.home')}><IconHome />{t('console.home')}</a><Button theme="borderless" icon={<IconBell />} aria-label={t('console.notifications')} /><Avatar size="small" color="light-blue">A</Avatar><span className="console-user">{t('console.account')}</span></Space></Layout.Header>
        <Layout.Content className="console-content">{props.children}</Layout.Content>
      </Layout>
    </Layout>
  )
}
