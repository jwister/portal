import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '../../../i18n'
import { PurchasePage } from '../PurchasePage'

describe('PurchasePage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('offers every preset amount, validates custom amount, and shows a PayPal option without trusting client-side quota or user IDs', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    render(<PurchasePage />)

    for (const amount of ['$5', '$10', '$50', '$100', '$200', '$500']) {
      expect(screen.getByRole('button', { name: amount })).toBeVisible()
    }

    await user.click(screen.getByRole('button', { name: 'Custom amount' }))
    const input = screen.getByLabelText('Custom amount')
    await user.type(input, '0')
    expect(screen.getByText('Enter an amount between $1 and $10,000.')).toBeVisible()

    await user.clear(input)
    await user.type(input, '25.5')
    await user.click(screen.getByRole('button', { name: 'Continue with PayPal' }))

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/payments/orders')
    const initObject = init as RequestInit
    expect(initObject.method).toBe('POST')
    expect(initObject.credentials).toBe('include')
    const body = String(initObject.body)
    expect(body).toContain('"amount":"25.5"')
    expect(body).toContain('"method":"PAYPAL"')
    expect(body).not.toContain('quota')
    expect(body).not.toContain('userId')
    expect(body).not.toContain('newapiUserId')
  })
})