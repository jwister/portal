import { useTranslation } from 'react-i18next'

import '../../i18n'
import { PaymentSelectionPanel } from './PaymentSelectionPanel'

export function PurchasePage() {
  const { t } = useTranslation()

  return (
    <main className="purchase-page">
      <header className="purchase-header">
        <h1>{t('purchase.title')}</h1>
        <p>{t('purchase.description')}</p>
      </header>
      <PaymentSelectionPanel />
    </main>
  )
}
