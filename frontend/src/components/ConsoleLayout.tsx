import { Avatar, Button, Layout, Nav, Space } from '@douyinfe/semi-ui'
import { IconBell, IconCreditCard, IconHistory, IconHome, IconKey, IconUser } from '@douyinfe/semi-icons'
import type { ReactNode } from 'react'

type ConsoleKey = 'dashboard' | 'recharge' | 'tokens' | 'logs' | 'profile'

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
}

export function ConsoleLayout(props: ConsoleLayoutProps) {
  return (
    <Layout className="console-shell">
      <Layout.Sider className="console-sider">
        <div className="console-brand"><span>Z</span><strong>Ztoken</strong></div>
        <nav aria-label="Console navigation"><Nav mode="vertical" selectedKeys={[props.activeKey]} onSelect={({ itemKey }) => window.location.assign(destinations[itemKey as ConsoleKey])} items={[
          { itemKey: 'dashboard', text: '仪表盘', icon: <IconHome /> },
          { itemKey: 'recharge', text: '余额充值', icon: <IconCreditCard /> },
          { itemKey: 'tokens', text: '令牌管理', icon: <IconKey /> },
          { itemKey: 'logs', text: '操作记录', icon: <IconHistory /> },
          { itemKey: 'profile', text: '账户信息', icon: <IconUser /> },
        ]} /></nav>
      </Layout.Sider>
      <Layout>
        <Layout.Header className="console-topbar"><h1>{props.activeKey === 'dashboard' ? '仪表盘' : props.activeKey === 'tokens' ? '令牌管理' : '控制台'}</h1><Space spacing="tight"><Button theme="borderless" icon={<IconBell />} aria-label="通知" /><Avatar size="small" color="light-blue">A</Avatar><span className="console-user">账户</span></Space></Layout.Header>
        <Layout.Content className="console-content">{props.children}</Layout.Content>
      </Layout>
    </Layout>
  )
}
