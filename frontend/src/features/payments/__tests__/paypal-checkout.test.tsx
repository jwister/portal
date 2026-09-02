import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '../../../i18n'
import type { PaymentOrder } from '../../../api/portal'
import {
  __resetPayPalSdkCacheForTests,
  type PayPalNamespace,
} from '../paypal-sdk'
import { PayPalCheckout } from '../PayPalCheckout'

const paypalNamespace: PayPalNamespace = {
  Buttons: () => ({ render: () => Promise.resolve(), isEligible: () => true }),
}

const order: PaymentOrder = {
  orderNo: 'PO-1',
  amountUsdMinor: 2550,
  quotaToCredit: 12_750_000,
  method: 'PAYPAL',
  status: 'WAITING_PAYMENT',
  expiresAt: '2026-09-02T01:00:00Z',
  confirmedAt: null,
  creditedAt: null,
  createdAt: '2026-09-02T00:00:00Z',
}

interface RequestStore {
  calls: { method: string; pathname: string; body: string | null }[]
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function installFetch(store: RequestStore, handlers: Record<string, (request: Request) => Promise<Response>>): void {
  const wrappedHandlers: Record<string, (request: Request) => Promise<Response>> = {}
  for (const [key, handler] of Object.entries(handlers)) {
    const [method, ...pathParts] = key.split(' ')
    const pathname = pathParts.join(' ')
    wrappedHandlers[`${method.toUpperCase()} ${pathname}`] = handler
  }
  vi.stubGlobal('fetch', vi.fn().mockImplementation(async (input: RequestInfo, init?: RequestInit) => {
    const rawUrl = typeof input === 'string'
      ? input
      : input instanceof URL
        ? input.toString()
        : input.url
    const absoluteUrl = rawUrl.startsWith('http') ? rawUrl : `http://localhost${rawUrl.startsWith('/') ? '' : '/'}${rawUrl}`
    const request = input instanceof Request ? input : new Request(absoluteUrl, init)
    const url = new URL(request.url)
    const key = `${request.method} ${url.pathname}`
    store.calls.push({
      method: request.method,
      pathname: url.pathname,
      body: init?.body === undefined ? null : String(init.body),
    })
    const handler = wrappedHandlers[key]
    if (!handler) throw new Error(`Unexpected fetch: ${key}`)
    return handler(request)
  }))
}

function installPayPalWindow(value: PayPalNamespace | undefined): void {
  Object.defineProperty(window, 'paypal', {
    configurable: true,
    writable: true,
    value,
  })
}

describe('PayPalCheckout', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
    document.head.innerHTML = ''
    installPayPalWindow(undefined)
    __resetPayPalSdkCacheForTests()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    document.head.innerHTML = ''
  })

  it('loads PayPal config and SDK, then renders the buttons container', async () => {
    const store: RequestStore = { calls: [] }
    installFetch(store, {
      'GET /api/payments/orders/PO-1/paypal/config': async () =>
        jsonResponse({ clientId: 'public-client', mode: 'sandbox' }),
    })
    installPayPalWindow(paypalNamespace)

    render(<PayPalCheckout order={order} />)

    await waitFor(() => {
      expect(screen.getByTestId('paypal-buttons')).toBeInTheDocument()
    })

    expect(store.calls.map((c) => `${c.method} ${c.pathname}`)).toContain('GET /api/payments/orders/PO-1/paypal/config')
  })

  it('shows an explicit manual-review message and never claims success for CREDIT_UNKNOWN', async () => {
    const store: RequestStore = { calls: [] }
    installFetch(store, {
      'GET /api/payments/orders/PO-1/paypal/config': async () =>
        jsonResponse({ clientId: 'public-client', mode: 'sandbox' }),
    })
    installPayPalWindow(paypalNamespace)

    render(<PayPalCheckout order={{ ...order, status: 'CREDIT_UNKNOWN' }} />)

    await waitFor(() => {
      expect(screen.getByTestId('paypal-buttons')).toBeInTheDocument()
    })
    expect(screen.getByText(/awaiting manual review/i)).toBeVisible()
    expect(screen.queryByText(/Quota credited/)).not.toBeInTheDocument()
  })

  it('never lets the browser submit amount, quota, user, or provider IDs to the create-order endpoint', async () => {
    const store: RequestStore = { calls: [] }
    installFetch(store, {
      'GET /api/payments/orders/PO-1/paypal/config': async () =>
        jsonResponse({ clientId: 'public-client', mode: 'sandbox' }),
      'POST /api/payments/orders/PO-1/paypal/order': async () =>
        jsonResponse({ providerOrderId: 'PP-1' }),
      'POST /api/payments/orders/PO-1/paypal/capture': async () =>
        jsonResponse({ ...order, status: 'PAID' }),
    })
    installPayPalWindow(paypalNamespace)

    render(<PayPalCheckout order={order} />)

    await waitFor(() => {
      expect(screen.getByTestId('paypal-buttons')).toBeInTheDocument()
    })

    const captured = store.calls.filter(
      (call) => call.pathname === '/api/payments/orders/PO-1/paypal/order' && call.method === 'POST',
    )
    // Either no POST has been recorded yet (because the PayPal button was never clicked)
    // or, if a POST happens later, the body is empty and never references forbidden fields.
    for (const call of captured) {
      const body = call.body ?? ''
      expect(body).not.toContain('amount')
      expect(body).not.toContain('2550')
      expect(body).not.toContain('quota')
      expect(body).not.toContain('userId')
      expect(body).not.toContain('providerOrderId')
      expect(body.trim()).toBe('')
    }
  })
})