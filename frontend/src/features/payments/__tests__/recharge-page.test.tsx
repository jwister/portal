import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '../../../i18n'
import { RechargePage } from '../RechargePage'

describe('RechargePage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('reuses the amount selector and marks payment methods as unavailable', () => {
    render(<RechargePage />)

    expect(screen.getByRole('heading', { name: 'Recharge balance' })).toBeVisible()
    expect(screen.getByRole('button', { name: '$500' })).toBeVisible()
    expect(screen.getByText('PayPal')).toBeVisible()
    expect(screen.getByText('Crypto / USDT')).toBeVisible()
    expect(screen.getAllByText('Coming soon')).toHaveLength(3)
    expect(vi.mocked(fetch)).not.toHaveBeenCalled()
  })
})
