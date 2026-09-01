import { render, screen, waitFor } from '@testing-library/react'
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
  })

  it('shows 控制台 when the portal session is authenticated', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ authenticated: true, profile: { id: 7, username: 'alice' } }), { status: 200 })))

    render(<PublicHeader />)

    expect(await screen.findByRole('button', { name: '控制台' })).toBeVisible()
  })
})
