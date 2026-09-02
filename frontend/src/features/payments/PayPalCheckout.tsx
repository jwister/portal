import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import {
  formatQuota,
  formatUsd,
  type PaymentOrder,
  type PayPalConfig,
} from '../../api/portal'
import { loadPayPalSdk, type PayPalNamespace, type PayPalButtons } from './paypal-sdk'

interface PayPalCheckoutProps {
  order: PaymentOrder
  onCompleted?: (order: PaymentOrder) => void
}

type CheckoutPhase =
  | 'loading-config'
  | 'preparing'
  | 'rendering'
  | 'cancelling'
  | 'polling'
  | 'final'
  | 'error'

interface RenderState {
  config: PayPalConfig
  paypal: PayPalNamespace
}

const POLL_INTERVAL_MS = 2_000
const TERMINAL_STATUSES = new Set<PaymentOrder['status']>([
  'PAID',
  'CREDIT_FAILED',
  'CREDIT_UNKNOWN',
  'EXPIRED',
  'CANCELLED',
])

function isPollingStatus(status: PaymentOrder['status']): boolean {
  return status === 'WAITING_PAYMENT' || status === 'CONFIRMED' || status === 'CREDITING'
}

export function PayPalCheckout({ order, onCompleted }: PayPalCheckoutProps) {
  const { t } = useTranslation()
  const [phase, setPhase] = useState<CheckoutPhase>('loading-config')
  const [error, setError] = useState<string | null>(null)
  const [currentOrder, setCurrentOrder] = useState<PaymentOrder>(order)
  const buttonsContainerRef = useRef<HTMLDivElement | null>(null)
  const buttonsRef = useRef<PayPalButtons | null>(null)
  const renderStateRef = useRef<RenderState | null>(null)

  useEffect(() => {
    let cancelled = false

    const bootstrap = async () => {
      try {
        setPhase('loading-config')
        const configResponse = await fetch(`/api/payments/orders/${encodeURIComponent(order.orderNo)}/paypal/config`, {
          credentials: 'include',
          cache: 'no-store',
        })
        if (!configResponse.ok) {
          throw new Error(`PayPal config unavailable (${configResponse.status})`)
        }
        const config = (await configResponse.json()) as PayPalConfig
        if (cancelled) return
        setPhase('preparing')
        const paypal = await loadPayPalSdk(config.clientId, config.mode)
        if (cancelled) return
        renderStateRef.current = { config, paypal }
        renderButtons(renderStateRef.current)
        setPhase('rendering')
      } catch (loadError) {
        if (cancelled) return
        setError(loadError instanceof Error ? loadError.message : String(loadError))
        setPhase('error')
      }
    }

    void bootstrap()

    return () => {
      cancelled = true
      destroyButtons()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [order.orderNo])

  useEffect(() => {
    if (!isPollingStatus(currentOrder.status)) return
    let cancelled = false
    const interval = window.setInterval(async () => {
      try {
        const detailResponse = await fetch(`/api/payments/orders/${encodeURIComponent(order.orderNo)}`, {
          credentials: 'include',
          cache: 'no-store',
        })
        if (!detailResponse.ok) return
        const fresh = (await detailResponse.json()) as PaymentOrder
        if (cancelled) return
        setCurrentOrder(fresh)
        if (TERMINAL_STATUSES.has(fresh.status)) {
          window.clearInterval(interval)
          setPhase('final')
          onCompleted?.(fresh)
        }
      } catch {
        // Polling errors stay silent; the user can refresh manually.
      }
    }, POLL_INTERVAL_MS)

    return () => {
      cancelled = true
      window.clearInterval(interval)
    }
  }, [currentOrder.status, order.orderNo, onCompleted])

  function renderButtons(state: RenderState) {
    const container = buttonsContainerRef.current
    if (!container) return
    destroyButtons()
    const buttons = state.paypal.Buttons({
      createOrder: async () => {
        const providerResponse = await fetch(`/api/payments/orders/${encodeURIComponent(order.orderNo)}/paypal/order`, {
          method: 'POST',
          credentials: 'include',
        })
        if (!providerResponse.ok) {
          throw new Error(`PayPal create-order failed (${providerResponse.status})`)
        }
        const provider = (await providerResponse.json()) as { providerOrderId: string }
        return provider.providerOrderId
      },
      onApprove: async () => {
        try {
          const captureResponse = await fetch(`/api/payments/orders/${encodeURIComponent(order.orderNo)}/paypal/capture`, {
            method: 'POST',
            credentials: 'include',
          })
          if (!captureResponse.ok) {
            throw new Error(`PayPal capture failed (${captureResponse.status})`)
          }
          const captured = (await captureResponse.json()) as PaymentOrder
          setCurrentOrder(captured)
          setPhase('polling')
          if (TERMINAL_STATUSES.has(captured.status)) {
            setPhase('final')
            onCompleted?.(captured)
          }
        } catch (captureError) {
          setError(captureError instanceof Error ? captureError.message : String(captureError))
          setPhase('error')
        }
      },
      onCancel: () => {
        setPhase('cancelling')
      },
      onError: (paypalError) => {
        setError(paypalError instanceof Error ? paypalError.message : String(paypalError))
        setPhase('error')
      },
    })
    buttonsRef.current = buttons
    void buttons.render(container)
  }

  function destroyButtons() {
    buttonsRef.current = null
    const container = buttonsContainerRef.current
    if (container) container.innerHTML = ''
  }

  const statusText = currentOrder.status === 'PAID'
    ? t('payment.status.paid', { quota: formatQuota(currentOrder.quotaToCredit) })
    : currentOrder.status === 'CONFIRMED' || currentOrder.status === 'CREDITING'
      ? t('payment.status.processing')
      : currentOrder.status === 'CREDIT_FAILED' || currentOrder.status === 'CREDIT_UNKNOWN'
        ? t('payment.status.manualReview')
        : currentOrder.status === 'EXPIRED'
          ? t('payment.status.expired')
          : currentOrder.status === 'CANCELLED'
            ? t('payment.status.cancelled')
            : t('payment.status.waiting')

  const amountText = formatUsd(currentOrder.amountUsdMinor)
  const quotaText = formatQuota(currentOrder.quotaToCredit)

  return (
    <section className="paypal-checkout" aria-label={t('payment.checkoutTitle')}>
      <header className="paypal-checkout-summary">
        <h3>{t('payment.checkoutTitle')}</h3>
        <p>
          <strong>{amountText}</strong>
          <span> · {t('payment.quotaEquivalent', { quota: quotaText })}</span>
        </p>
        <p className="paypal-checkout-status" data-status={currentOrder.status}>{statusText}</p>
      </header>
      <div ref={buttonsContainerRef} className="paypal-buttons" data-testid="paypal-buttons" />
      {phase === 'loading-config' || phase === 'preparing' || phase === 'rendering'
        ? <p className="paypal-checkout-hint">{t('payment.sdkLoading')}</p>
        : null}
      {phase === 'cancelling'
        ? <p className="paypal-checkout-hint">{t('payment.sdkCancelled')}</p>
        : null}
      {phase === 'error' && error
        ? <p className="paypal-checkout-error" role="alert">{t('payment.sdkError', { message: error })}</p>
        : null}
    </section>
  )
}