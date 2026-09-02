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
  })

  it('renders token metadata with only the masked key', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      items: [{ id: 3, name: 'server', enabled: true, remainingQuota: 500, usedQuota: 20, unlimited: false, expiredTime: -1, maskedKey: 'sk-abcd********wxyz' }],
    }), { status: 200 })))

    render(<TokensPage />)

    expect(await screen.findByText('server')).toBeVisible()
    expect(screen.getByText('500')).toBeVisible()
    expect(screen.getByText('Active')).toBeVisible()
    expect(screen.getByText('sk-abcd********wxyz')).toBeVisible()
    expect(screen.queryByText('sk-full-secret')).not.toBeInTheDocument()
  })

  it('creates a token through the Portal BFF and refreshes the list', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ items: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
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
