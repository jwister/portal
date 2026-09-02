import { useTranslation } from 'react-i18next'

import '../../i18n'
import { ConsolePageHeader } from '../../components/ConsolePageHeader'
import { PaymentSelectionPanel } from './PaymentSelectionPanel'

export function RechargePage() {
  const { t } = useTranslation()

  return (
    <main>
      <ConsolePageHeader title={t('console.recharge')} description={t('purchase.description')} />
      <PaymentSelectionPanel />
    </main>
  )
}
