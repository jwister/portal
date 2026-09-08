import { Button, Card, Space, Tag, Typography } from '@douyinfe/semi-ui'
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
  const [customAmount, setCustomAmount] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const customValid = selected !== 'custom' || isValidCustomAmount(customAmount)
  const amount = selected === 'custom' ? customAmount : String(selected)
  const amountIsUsable = customValid && amount !== ''

  const handleMethod = async (method: PaymentMethod) => {
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
            onClick={() => { void handleMethod('PAYPAL') }}
          >
            {t('payment.continuePaypal')}
          </Button>
        </Card>
        <Card className="payment-method-card" title={t('payment.crypto')}>
          <Typography.Paragraph type="tertiary" className="payment-method-description">{t('payment.trc20Description')}</Typography.Paragraph>
          <Button theme="solid" type="primary" block disabled={!amountIsUsable || submitting} loading={submitting} onClick={() => { void handleMethod('USDT_TRC20') }}>
            {t('payment.continueTrc20')}
          </Button>
        </Card>
        <Card className="payment-method-card" title={t('payment.other')}>
          <Tag color="grey">{t('payment.comingSoon')}</Tag>
        </Card>
      </div>
      {error && <Typography.Text type="danger" role="alert">{error}</Typography.Text>}
    </section>
  )
}
