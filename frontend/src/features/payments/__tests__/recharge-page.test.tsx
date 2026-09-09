import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

  it('shows the shared amount selector with PayPal and TRC20 icon options', async () => {
    const fetchMock = vi.mocked(fetch)
    const user = userEvent.setup()
    render(<RechargePage />)

    expect(screen.getByRole('heading', { name: 'Recharge balance' })).toBeVisible()
    expect(screen.getByRole('button', { name: '$500' })).toBeVisible()
    expect(screen.getByRole('radio', { name: 'PayPal' })).toBeChecked()
    expect(screen.getByRole('radio', { name: 'TRC20 USDT' })).not.toBeChecked()
    expect(screen.getByRole('button', { name: 'Confirm payment' })).toBeVisible()
    expect(screen.queryByText(/Authorize the charge/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Continue with PayPal' })).not.toBeInTheDocument()
    expect(screen.queryByText('Other payment method')).not.toBeInTheDocument()
    expect(screen.queryByText('Coming soon')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '$50' }))
    await user.click(screen.getByRole('button', { name: 'Confirm payment' }))

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [, init] = fetchMock.mock.calls[0]
    const body = String((init as RequestInit).body)
    expect(body).toContain('"amount":"50"')
    expect(body).toContain('"method":"PAYPAL"')
  })
})
