import { Button, Space, Toast, Typography } from '@douyinfe/semi-ui'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { AmountSelector, isValidCustomAmount, type AmountSelection } from './AmountSelector'
import { PaymentMethodPlaceholder } from './PaymentMethodPlaceholder'

export function PaymentSelectionPanel() {
  const { t } = useTranslation()
  const [selected, setSelected] = useState<AmountSelection>(5)
  const [customAmount, setCustomAmount] = useState('')
  const customValid = selected !== 'custom' || isValidCustomAmount(customAmount)
  const amount = selected === 'custom' ? customAmount : String(selected)

  return (
    <section className="purchase-panel">
      <AmountSelector selected={selected} customAmount={customAmount} onSelect={setSelected} onCustomAmount={setCustomAmount} />
      <Typography.Text type="tertiary">{t('purchase.selected')}: ${amount || '—'}</Typography.Text>
      <PaymentMethodPlaceholder />
      <Space>
        <Button theme="solid" type="primary" disabled={!customValid} onClick={() => Toast.info(t('payment.unavailable'))}>
          {t('payment.continue')}
        </Button>
      </Space>
    </section>
  )
}
