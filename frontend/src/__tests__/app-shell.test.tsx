import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import i18n, { LOCALE_STORAGE_KEY } from '../i18n'

describe('portal application shell', () => {
  beforeEach(async () => {
    localStorage.clear()
    await i18n.changeLanguage('en')
  })

  it('shows public navigation and the primary console action', () => {
    render(<App />)

    expect(screen.getByRole('link', { name: 'Models' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Purchase' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Create account' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Console' })).not.toBeInTheDocument()
  })

  it('switches language and persists the visitor preference', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: '中文' }))

    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('zh-CN')
    expect(screen.getByRole('link', { name: '模型' })).toBeVisible()
  })

  it('renders the console dashboard at its direct route', async () => {
    window.history.pushState({}, '', '/console/dashboard')
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true, profile: { id: 7, username: 'alice' } }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        availableQuota: 900,
        usedQuota: 100,
        requestCount: 12,
        tokenUsage: null,
      }), { status: 200 })))

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Console overview' })).toBeVisible()
  })
})
