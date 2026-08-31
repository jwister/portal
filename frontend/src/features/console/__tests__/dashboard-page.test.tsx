import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { DashboardPage } from '../DashboardPage'

describe('DashboardPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders the current users balance and usage metrics', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      availableQuota: 900,
      usedQuota: 100,
      requestCount: 12,
    }), { status: 200 })))

    render(<DashboardPage />)

    expect(await screen.findByText('900')).toBeVisible()
    expect(screen.getByText('100')).toBeVisible()
    expect(screen.getByText('12')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Console overview' })).toBeVisible()
  })
})
