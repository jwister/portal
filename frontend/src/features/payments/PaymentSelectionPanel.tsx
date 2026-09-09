import { Button, Typography } from '@douyinfe/semi-ui'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { AmountSelector, isValidCustomAmount, type AmountSelection } from './AmountSelector'
import { createPaymentOrder, type PaymentOrder, type PaymentMethod } from '../../api/portal'

interface PaymentSelectionPanelProps {
  onConfirm: (order: PaymentOrder) => void
}

export function PaymentSelectionPanel({ onConfirm }: PaymentSelectionPanelProps) {
  const { t } = useTranslation()
  const [selected, setSelected] = useState<AmountSelection>(5)
  const [method, setMethod] = useState<PaymentMethod>('PAYPAL')
  const [customAmount, setCustomAmount] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const customValid = selected !== 'custom' || isValidCustomAmount(customAmount)
  const amount = selected === 'custom' ? customAmount : String(selected)
  const amountIsUsable = customValid && amount !== ''

  const handleConfirm = async () => {
    if (!amountIsUsable) return
    setError(null)
    setSubmitting(true)
    try {
      onConfirm(await createPaymentOrder({ amount, method }))
    } catch {
      setError(t('payment.createFailed'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="purchase-panel">
      <ol className="purchase-ledger-steps" data-testid="purchase-ledger-steps" aria-label={t('purchase.title')}>
        <li className="is-current"><span>1</span>{t('purchase.amount')}</li>
        <li><span>2</span>{t('payment.title')}</li>
      </ol>
      <AmountSelector
        selected={selected}
        customAmount={customAmount}
        onSelect={setSelected}
        onCustomAmount={setCustomAmount}
      />
      <aside className="purchase-ledger-summary" data-testid="purchase-ledger-summary" aria-live="polite">
        <Typography.Text type="tertiary">{t('purchase.selected')}</Typography.Text>
        <strong>${amount || '—'}</strong>
      </aside>
      <div className="payment-method-grid" aria-labelledby="payment-method-title">
        <Typography.Title heading={4} id="payment-method-title">{t('payment.title')}</Typography.Title>
        <label className={`payment-method-card ${method === 'PAYPAL' ? 'is-selected' : ''}`}>
          <input type="radio" name="payment-method" aria-label="PayPal" checked={method === 'PAYPAL'} onChange={() => setMethod('PAYPAL')} />
          <img className="payment-method-logo" src="/Paypal.png" alt="" />
          <span>PayPal</span>
        </label>
        <label className={`payment-method-card ${method === 'USDT_TRC20' ? 'is-selected' : ''}`}>
          <input type="radio" name="payment-method" aria-label="TRC20 USDT" checked={method === 'USDT_TRC20'} onChange={() => setMethod('USDT_TRC20')} />
          <img className="payment-method-logo" src="/Tron.png" alt="" />
          <span>TRC20 USDT</span>
        </label>
      </div>
      <Button className="payment-confirm-button" theme="solid" type="primary" block disabled={!amountIsUsable || submitting} loading={submitting} onClick={() => { void handleConfirm() }}>
        {t('payment.confirm')}
      </Button>
      {error && <Typography.Text type="danger" role="alert">{error}</Typography.Text>}
    </section>
  )
}
