import { Button, Nav, Space } from '@douyinfe/semi-ui'

import { useAuthStatus } from '../auth/use-auth-status'
import '../i18n'

function navigate(path: string): void {
  window.location.assign(path)
}

export function PublicHeader() {
  const status = useAuthStatus()
  const action = status.kind === 'authenticated'
    ? { label: '控制台', path: '/console/dashboard' }
    : { label: '登录', path: '/sign-in' }

  return (
    <header className="public-header">
      <Nav
        mode="horizontal"
        className="public-nav"
        header={<a className="semi-brand" href="/" aria-label="Ztoken"><span>Z</span>Ztoken</a>}
        items={[
          { itemKey: '/', text: '首页' },
          { itemKey: '/models', text: '模型' },
          { itemKey: 'https://docs.newapi.pro/zh/docs/api', text: '文档' },
          { itemKey: '/purchase', text: '购买' },
        ]}
        onSelect={(data) => {
          const path = String(data.itemKey)
          if (path.startsWith('http')) window.open(path, '_blank', 'noopener,noreferrer')
          else navigate(path)
        }}
        footer={(
          <Space spacing="tight">
            <Button theme="borderless" aria-label="切换语言">中 / EN</Button>
            <Button theme="solid" type="primary" loading={status.kind === 'loading'} onClick={() => navigate(action.path)}>{action.label}</Button>
          </Space>
        )}
      />
    </header>
  )
}
