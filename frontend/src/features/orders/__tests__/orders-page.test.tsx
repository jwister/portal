import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '../../../i18n'
import { OrdersPage } from '../OrdersPage'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('OrdersPage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
  })

  it('lists the current user orders from the payment API instead of an empty placeholder', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        items: [
          {
            orderNo: 'PO-1',
            amountUsdMinor: 2550,
            quotaToCredit: 12_750_000,
            method: 'PAYPAL',
            status: 'PAID',
            expiresAt: '2026-09-02T01:00:00Z',
            confirmedAt: '2026-09-02T00:05:00Z',
            creditedAt: '2026-09-02T00:06:00Z',
            createdAt: '2026-09-02T00:00:00Z',
          },
        ],
        page: 1,
        pageSize: 20,
        total: 1,
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    render(<OrdersPage />)

    await waitFor(() => {
      expect(screen.getByText('PO-1')).toBeVisible()
    })

    expect(screen.getByText('$25.50')).toBeVisible()
    expect(screen.getByText('12,750,000')).toBeVisible()
    expect(fetchMock).toHaveBeenCalledWith('/api/payments/orders?page=1&pageSize=20', expect.objectContaining({
      credentials: 'include',
    }))
  })

  it('shows an empty state when the customer has no orders yet', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ items: [], page: 1, pageSize: 20, total: 0 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    render(<OrdersPage />)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Go to recharge/ })).toBeVisible()
    })
    expect(screen.queryByText('Order management is coming soon.')).not.toBeInTheDocument()
  })
})