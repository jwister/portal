import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '../../i18n'
import { PublicHeader } from '../PublicHeader'

describe('PublicHeader', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('zh-CN')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows 登录 when the portal session is anonymous', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })))

    render(<PublicHeader />)

    await waitFor(() => expect(screen.getByRole('button', { name: '登录' })).toBeVisible())
    expect(screen.queryByRole('button', { name: '控制台' })).not.toBeInTheDocument()
    expect(screen.queryByText('alice')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '退出登录' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '控制台' })).not.toBeInTheDocument()
  })

  it('shows the account cluster and keeps Console out of the navigation when authenticated', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ authenticated: true, profile: { id: 7, username: 'alice' } }), { status: 200 })))

    render(<PublicHeader />)

    expect(await screen.findByRole('button', { name: '控制台' })).toBeVisible()
    expect(screen.getByText('alice')).toBeVisible()
    expect(screen.getByRole('button', { name: '退出登录' })).toBeVisible()
    expect(screen.getByLabelText('alice 的用户头像')).toBeVisible()
    expect(screen.queryByRole('link', { name: '控制台' })).not.toBeInTheDocument()
    expect(screen.queryByText('创建账户')).not.toBeInTheDocument()
  })

  it('signs out and reloads the page from the authenticated account cluster', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true, profile: { id: 7, username: 'alice' } }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const reload = vi.fn()
    vi.stubGlobal('location', { ...window.location, reload })

    render(<PublicHeader />)
    await user.click(await screen.findByRole('button', { name: '退出登录' }))

    await waitFor(() => expect(reload).toHaveBeenCalledTimes(1))
    expect(fetchMock).toHaveBeenLastCalledWith('/api/auth/sign-out', expect.objectContaining({ method: 'POST', credentials: 'include' }))
  })
})
