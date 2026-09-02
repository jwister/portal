import { Button, Empty } from '@douyinfe/semi-ui'
import { IconCreditCard } from '@douyinfe/semi-icons'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { ConsolePageHeader } from '../../components/ConsolePageHeader'

export function OrdersPage() {
  const { t } = useTranslation()

  return (
    <main>
      <ConsolePageHeader title={t('orders.title')} description={t('orders.description')} />
      <Empty description={t('orders.empty')}>
        <Button theme="solid" type="primary" icon={<IconCreditCard />} onClick={() => window.location.assign('/console/recharge')}>
          {t('orders.recharge')}
        </Button>
      </Empty>
    </main>
  )
}
