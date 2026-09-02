import { Button, Card, Space, Tag, Typography } from '@douyinfe/semi-ui'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { AmountSelector, isValidCustomAmount, type AmountSelection } from './AmountSelector'
import type { PaymentOrder, PaymentMethod } from '../../api/portal'

interface PaymentSelectionPanelProps {
  onConfirm: (order: PaymentOrder) => void
}

export function PaymentSelectionPanel({ onConfirm }: PaymentSelectionPanelProps) {
  const { t } = useTranslation()
  const [selected, setSelected] = useState<AmountSelection>(5)
  const [customAmount, setCustomAmount] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const customValid = selected !== 'custom' || isValidCustomAmount(customAmount)
  const amount = selected === 'custom' ? customAmount : String(selected)
  const amountIsUsable = customValid && amount !== ''

  const handlePayPal = async () => {
    if (!amountIsUsable) return
    setError(null)
    setSubmitting(true)
    try {
      const response = await fetch('/api/payments/orders', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ amount, method: 'PAYPAL' satisfies PaymentMethod }),
      })
      if (!response.ok) {
        setError(t('payment.createFailed'))
        return
      }
      const order = (await response.json()) as PaymentOrder
      onConfirm(order)
    } catch {
      setError(t('payment.createFailed'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="purchase-panel">
      <AmountSelector
        selected={selected}
        customAmount={customAmount}
        onSelect={setSelected}
        onCustomAmount={setCustomAmount}
      />
      <Typography.Text type="tertiary">
        {t('purchase.selected')}: ${amount || '—'}
      </Typography.Text>
      <div className="payment-method-grid" aria-labelledby="payment-method-title">
        <Typography.Title heading={4} id="payment-method-title">{t('payment.title')}</Typography.Title>
        <Card className="payment-method-card" title={t('payment.paypal')}>
          <Space spacing={8} align="center">
            <Tag color="green">{t('payment.paypalAvailable')}</Tag>
          </Space>
          <Typography.Paragraph type="tertiary" className="payment-method-description">
            {t('payment.paypalDescription')}
          </Typography.Paragraph>
          <Button
            theme="solid"
            type="primary"
            block
            disabled={!amountIsUsable || submitting}
            loading={submitting}
            onClick={handlePayPal}
          >
            {t('payment.continuePaypal')}
          </Button>
        </Card>
        <Card className="payment-method-card" title={t('payment.crypto')}>
          <Tag color="grey">{t('payment.comingSoon')}</Tag>
        </Card>
        <Card className="payment-method-card" title={t('payment.other')}>
          <Tag color="grey">{t('payment.comingSoon')}</Tag>
        </Card>
      </div>
      {error && <Typography.Text type="danger" role="alert">{error}</Typography.Text>}
    </section>
  )
}