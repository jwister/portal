import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '../../../i18n'
import { LogsPage } from '../LogsPage'

describe('LogsPage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders safe log fields, usage statistics, and applies model filters', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn((path: string) => {
      if (path.startsWith('/api/console/logs/stats')) {
        return Promise.resolve(new Response(JSON.stringify({ quota: 120, rpm: 3, tpm: 1500 }), { status: 200 }))
      }
      return Promise.resolve(new Response(JSON.stringify({
        page: 1,
        pageSize: 50,
        total: 1,
        items: [{ id: 1, createdAt: 1710000000, type: 2, content: 'completed', tokenName: 'server', modelName: 'gpt-4o', quota: 120, promptTokens: 100, completionTokens: 20, useTime: 50, stream: false, requestId: 'req-1' }],
      }), { status: 200 }))
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<LogsPage />)

    expect(await screen.findByText('gpt-4o')).toBeVisible()
    expect(screen.getByText('1,500')).toBeVisible()
    expect(screen.queryByText('private-channel')).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('Model name'), 'gpt-4.1')
    await user.click(screen.getByRole('button', { name: 'Apply filters' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('modelName=gpt-4.1'), expect.anything()))
  })
})
