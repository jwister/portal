import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { TokensPage } from '../TokensPage'

describe('TokensPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders token metadata without exposing an API key', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      items: [{ id: 3, name: 'server', enabled: true, remainingQuota: 500 }],
    }), { status: 200 })))

    render(<TokensPage />)

    expect(await screen.findByText('server')).toBeVisible()
    expect(screen.getByText('500')).toBeVisible()
    expect(screen.getByText('Active')).toBeVisible()
  })
})
