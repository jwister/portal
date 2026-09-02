import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '../../../i18n'
import { DashboardPage } from '../DashboardPage'

describe('DashboardPage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders four account metrics and labels unavailable token usage honestly', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      availableQuota: 900,
      usedQuota: 100,
      requestCount: 12,
      tokenUsage: null,
    }), { status: 200 })))

    render(<DashboardPage />)

    expect(await screen.findByText('900')).toBeVisible()
    expect(screen.getByText('100')).toBeVisible()
    expect(screen.getByText('12')).toBeVisible()
    expect(screen.getByText('Token usage')).toBeVisible()
    expect(screen.getByText('Not available')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Console overview' })).toBeVisible()
  })
})
