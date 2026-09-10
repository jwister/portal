import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '../../../i18n'
import { TokensPage } from '../TokensPage'

describe('TokensPage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('renders token metadata with only the masked key', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      page: 1,
      pageSize: 50,
      total: 1,
      items: [{ id: 3, name: 'server', enabled: true, remainingQuota: 500, usedQuota: 20, unlimited: false, expiredTime: -1, maskedKey: 'sk-abcd********wxyz' }],
    }), { status: 200 })))

    render(<TokensPage />)

    expect(await screen.findByText('server')).toBeVisible()
    // 令牌额度表格采用美元金额展示，而不是内部 quota 整数。
    expect(screen.getAllByText('$0.00').some((element) => element.tagName === 'TD')).toBe(true)
    expect(screen.getByText('Active')).toBeVisible()
    expect(screen.getByText('API Key')).toBeVisible()
    expect(screen.getByText('sk-abcd********wxyz')).toBeVisible()
    expect(screen.queryByText('sk-full-secret')).not.toBeInTheDocument()
  })

  it('copies a revealed API key with the native fallback when Clipboard API is unavailable', async () => {
    const user = userEvent.setup()
    const execCommand = vi.fn().mockImplementation((command: string) => {
      const activeElement = document.activeElement
      // 部分嵌入式浏览器要求复制目标获得焦点并完整选中，否则会拒绝复制。
      return command === 'copy'
        && activeElement instanceof HTMLTextAreaElement
        && activeElement.selectionStart === 0
        && activeElement.selectionEnd === activeElement.value.length
    })
    Object.defineProperty(document, 'execCommand', { configurable: true, value: execCommand })
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: undefined })
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        page: 1,
        pageSize: 50,
        total: 1,
        items: [{ id: 3, name: 'server', enabled: true, remainingQuota: 500, usedQuota: 20, unlimited: false, expiredTime: -1, maskedKey: 'sk-abcd********wxyz' }],
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ key: 'sk-live-key' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    render(<TokensPage />)
    await screen.findByText('server')
    await user.click(screen.getByRole('button', { name: 'Show key' }))
    expect(await screen.findByText('sk-live-key')).toBeVisible()

    await user.click(screen.getByRole('button', { name: /copy/i }))

    expect(execCommand).toHaveBeenCalledWith('copy')
    expect(await screen.findByText('Key copied.')).toBeVisible()
  })

  it('shows a compact token summary above the management table', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      page: 1,
      pageSize: 50,
      total: 7,
      items: [
        { id: 3, name: 'server', enabled: true, remainingQuota: 500, usedQuota: 20, unlimited: false, expiredTime: -1, maskedKey: 'sk-abcd********wxyz' },
        { id: 4, name: 'archive', enabled: false, remainingQuota: 0, usedQuota: 30, unlimited: true, expiredTime: -1, maskedKey: 'sk-efgh********ijkl' },
      ],
    }), { status: 200 })))

    render(<TokensPage />)

    expect(await screen.findByText('Active tokens (this page)')).toBeVisible()
    expect(screen.getByText('Limited quota remaining (this page)')).toBeVisible()
    expect(screen.getByText('Total tokens').closest('.metric-card')).toHaveTextContent('7')
  })

  it('creates a token through the Portal BFF and refreshes the list', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ page: 1, pageSize: 50, total: 0, items: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        page: 1,
        pageSize: 50,
        total: 1,
        items: [{ id: 4, name: 'new-key', enabled: true, remainingQuota: 0, usedQuota: 0, unlimited: true, expiredTime: -1, maskedKey: 'sk-new********key' }],
      }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    render(<TokensPage />)
    await screen.findByText('No API tokens yet.')
    await user.click(screen.getByRole('button', { name: /Create token/ }))
    await user.type(screen.getByLabelText('Token name'), 'new-key')
    await user.click(screen.getByRole('button', { name: 'Create' }))

    await waitFor(() => expect(screen.getByText('new-key')).toBeVisible())
    expect(fetchMock).toHaveBeenCalledWith('/api/console/tokens', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: expect.stringContaining('"name":"new-key"'),
    }))
  })
})
