import { Typography } from '@douyinfe/semi-ui'
import { Fragment, useState } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import type { PaymentOrder } from '../../api/portal'
import { PaymentSelectionPanel } from './PaymentSelectionPanel'
import { PayPalCheckout } from './PayPalCheckout'
import { Trc20Checkout } from './Trc20Checkout'

export function PurchasePage() {
  const { t } = useTranslation()
  const [order, setOrder] = useState<PaymentOrder | null>(null)

  return (
    <main className="purchase-page">
      <header className="purchase-header">
        <h1>{t('purchase.title')}</h1>
        <p>{t('purchase.copy')}</p>
      </header>
      {order ? (
        <Fragment>
          {order.method === 'USDT_TRC20' ? <Trc20Checkout order={order} onCompleted={setOrder} /> : <PayPalCheckout order={order} onCompleted={setOrder} />}
          <button type="button" className="purchase-back" onClick={() => setOrder(null)}>
            {t('payment.changeAmount')}
          </button>
        </Fragment>
      ) : (
        <PaymentSelectionPanel onConfirm={setOrder} />
      )}
      <Typography.Paragraph type="tertiary" className="purchase-footnote">
        {t('purchase.footnote')}
      </Typography.Paragraph>
    </main>
  )
}
