import { Button, Input, Typography } from '@douyinfe/semi-ui'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { formatQuota, getPaymentOrder, getTrc20PaymentStatus, submitTrc20Txid, type PaymentOrder, type Trc20PaymentInstruction } from '../../api/portal'

interface Trc20CheckoutProps { order: PaymentOrder; onCompleted?: (order: PaymentOrder) => void }
const terminal = new Set<PaymentOrder['status']>(['PAID', 'CREDIT_FAILED', 'CREDIT_UNKNOWN', 'EXPIRED', 'CANCELLED'])

/** 以服务端已分配订单为唯一来源展示 TRC20 收款信息，并提供即时 TxID 核验。 */
export function Trc20Checkout({ order, onCompleted }: Trc20CheckoutProps) {
  const { t } = useTranslation()
  const [instruction, setInstruction] = useState<Trc20PaymentInstruction | null>(null)
  const [current, setCurrent] = useState(order)
  const [txid, setTxid] = useState('')
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => { void getTrc20PaymentStatus(order.orderNo).then(setInstruction).catch(() => setMessage(t('payment.trc20LoadError'))) }, [order.orderNo, t])
  useEffect(() => {
    if (terminal.has(current.status)) return
    const id = window.setInterval(() => { void getPaymentOrder(order.orderNo).then(next => { setCurrent(next); if (terminal.has(next.status)) onCompleted?.(next) }).catch(() => undefined) }, 5_000)
    return () => window.clearInterval(id)
  }, [current.status, onCompleted, order.orderNo])

  const copy = async (value: string) => {
    try { await navigator.clipboard.writeText(value); setMessage(t('payment.trc20Copied')) } catch { setMessage(t('payment.trc20CopyError')) }
  }
  const verify = async () => {
    if (!txid.trim()) return
    try { const result = await submitTrc20Txid(order.orderNo, txid.trim()); setMessage(t(`payment.trc20Result.${result.result}`)) } catch { setMessage(t('payment.trc20VerifyError')) }
  }
  const status = current.status === 'PAID' ? t('payment.status.paid', { quota: formatQuota(current.quotaToCredit) })
    : current.status === 'CONFIRMED' || current.status === 'CREDITING' ? t('payment.status.processing')
      : current.status === 'EXPIRED' ? t('payment.status.expired') : t('payment.trc20Waiting')

  return <section className="trc20-checkout" aria-label={t('payment.trc20Title')}>
    <header><p className="trc20-kicker">TRON · TRC20</p><h3>{t('payment.trc20Title')}</h3><p>{t('payment.quotaEquivalent', { quota: formatQuota(current.quotaToCredit) })}</p></header>
    {instruction && <div className="trc20-instruction">
      <div><span>{t('payment.trc20Amount')}</span><strong>{instruction.payableAmount} {instruction.payableCurrency}</strong><Button theme="borderless" size="small" onClick={() => { void copy(instruction.payableAmount) }}>{t('payment.trc20CopyAmount')}</Button></div>
      <div><span>{t('payment.trc20Address')}</span><code>{instruction.receiveAddress}</code><Button theme="borderless" size="small" onClick={() => { void copy(instruction.receiveAddress) }}>{t('payment.trc20CopyAddress')}</Button></div>
    </div>}
    <p className="trc20-status">{status}</p>
    <div className="trc20-txid"><Input aria-label={t('payment.trc20Txid')} value={txid} onChange={setTxid} placeholder={t('payment.trc20TxidPlaceholder')} /><Button type="primary" disabled={!txid.trim()} onClick={() => { void verify() }}>{t('payment.trc20Verify')}</Button></div>
    {message && <Typography.Text type="tertiary">{message}</Typography.Text>}
  </section>
}
