import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '../../i18n'
import { ConsoleLayout } from '../ConsoleLayout'

describe('ConsoleLayout', () => {
  beforeEach(async () => { await i18n.changeLanguage('zh-CN') })

  it('renders readable console navigation with the active item selected', () => {
    render(<ConsoleLayout activeKey="dashboard"><div>content</div></ConsoleLayout>)

    const navigation = screen.getByRole('navigation', { name: '控制台导航' })
    expect(navigation).toBeVisible()
    expect(within(navigation).getByText('仪表盘')).toBeVisible()
    expect(within(navigation).getByText('令牌管理')).toBeVisible()
    expect(within(navigation).getByText('仪表盘').closest('[role="menuitem"]')).toHaveClass('semi-navigation-item-selected')
    expect(screen.queryAllByText('门户首页')).toHaveLength(0)
  })

  it('renders the console avatar with account actions', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ authenticated: true, profile: { id: 7, username: 'alice' } }), { status: 200 })))
    render(<ConsoleLayout activeKey="dashboard"><div>content</div></ConsoleLayout>)

    const avatar = await screen.findByLabelText('alice 的用户头像')
    await user.hover(avatar)
    expect(screen.getByText('alice')).toBeVisible()
    expect(screen.getByRole('button', { name: '退出登录' })).toBeVisible()
    expect(screen.queryAllByText('门户首页')).toHaveLength(0)
  })

  it('uses in-app navigation for sidebar menu changes', () => {
    const navigate = vi.fn()
    render(<ConsoleLayout activeKey="dashboard" onNavigate={navigate}><div>content</div></ConsoleLayout>)

    const navigation = screen.getByRole('navigation', { name: '控制台导航' })
    within(navigation).getByText('令牌管理').click()

    expect(navigate).toHaveBeenCalledWith('/console/tokens')
  })
})
