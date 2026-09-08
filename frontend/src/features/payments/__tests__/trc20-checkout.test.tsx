import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { PaymentOrder } from '../../../api/portal'
import i18n from '../../../i18n'
import { Trc20Checkout } from '../Trc20Checkout'

const order: PaymentOrder = {
  orderNo: 'PO_TRON_1', amountUsdMinor: 2550, quotaToCredit: 12_750_000, method: 'USDT_TRC20',
  status: 'WAITING_PAYMENT', expiresAt: '2026-09-08T01:00:00Z', confirmedAt: null, creditedAt: null, createdAt: '2026-09-08T00:00:00Z',
}

function response(body: unknown): Response {
  return new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })
}

describe('Trc20Checkout', () => {
  beforeEach(async () => { await i18n.changeLanguage('en') })
  afterEach(() => { vi.unstubAllGlobals() })

  it('renders only server-issued payment instructions and submits the TxID to its order endpoint', async () => {
    const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/payments/orders/PO_TRON_1/trc20/status') return Promise.resolve(response({ receiveAddress: 'TJxA4YfDSE5v9cYrgeArHQkCZcqW45wBzQ', payableAmount: '25.5001', payableCurrency: 'USDT', status: 'WAITING_PAYMENT', expiresAt: order.expiresAt, txidCheckResult: null }))
      if (url === '/api/payments/orders/PO_TRON_1/trc20/txid') return Promise.resolve(response({ result: 'PENDING_CONFIRMATION' }))
      if (url === '/api/payments/orders/PO_TRON_1') return Promise.resolve(response(order))
      return Promise.reject(new Error(`Unexpected request: ${url}`))
    })
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    render(<Trc20Checkout order={order} />)

    expect(await screen.findByText('25.5001 USDT')).toBeVisible()
    expect(screen.getByText('TJxA4YfDSE5v9cYrgeArHQkCZcqW45wBzQ')).toBeVisible()
    expect(screen.getByTestId('trc20-ledger-summary')).toHaveTextContent('Pay with TRC20 USDT')
    expect(screen.getByTestId('trc20-ledger-summary')).toHaveTextContent('Waiting for a confirmed TRC20 transfer')
    await user.type(screen.getByLabelText('Transaction ID'), 'a'.repeat(64))
    await user.click(screen.getByRole('button', { name: 'Verify transaction' }))

    await waitFor(() => expect(screen.getByText('Transfer found; waiting for 20 confirmations.')).toBeVisible())
    const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/payments/orders/PO_TRON_1/trc20/txid') as [string, RequestInit]
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify({ txid: 'a'.repeat(64) }))
  })
})
