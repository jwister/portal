import { Button, Input, Typography } from '@douyinfe/semi-ui'
import { useTranslation } from 'react-i18next'

import '../../i18n'

export const presetAmounts = [5, 10, 50, 100, 200, 500] as const
export type AmountSelection = number | 'custom'

interface AmountSelectorProps {
  selected: AmountSelection
  customAmount: string
  onSelect: (amount: AmountSelection) => void
  onCustomAmount: (amount: string) => void
}

export function isValidCustomAmount(value: string): boolean {
  if (!/^\d+(?:\.\d{1,2})?$/.test(value)) return false
  const amount = Number(value)
  return amount >= 1 && amount <= 10_000
}

export function AmountSelector({ selected, customAmount, onSelect, onCustomAmount }: AmountSelectorProps) {
  const { t } = useTranslation()
  const customHasError = selected === 'custom' && customAmount !== '' && !isValidCustomAmount(customAmount)

  return (
    <section className="amount-selector" aria-labelledby="purchase-amount-title">
      <Typography.Title heading={4} id="purchase-amount-title">{t('purchase.amount')}</Typography.Title>
      <div className="amount-grid">
        {presetAmounts.map((amount) => <Button key={amount} theme={selected === amount ? 'solid' : 'light'} type="primary" onClick={() => onSelect(amount)}>${amount}</Button>)}
        <Button theme={selected === 'custom' ? 'solid' : 'light'} type="primary" onClick={() => onSelect('custom')}>{t('purchase.custom')}</Button>
      </div>
      {selected === 'custom' && <div className="amount-custom-input"><label htmlFor="custom-amount">{t('purchase.customLabel')}<Input id="custom-amount" type="number" inputMode="decimal" value={customAmount} onChange={onCustomAmount} /></label>{customHasError && <Typography.Text type="danger" role="alert">{t('purchase.amountError')}</Typography.Text>}</div>}
    </section>
  )
}
