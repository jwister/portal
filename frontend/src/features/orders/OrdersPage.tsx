import { Button, Card, Empty, Space, Table, Tag, Typography } from '@douyinfe/semi-ui'
import { IconAlertCircle, IconClock, IconCreditCard, IconRefresh, IconTickCircle } from '@douyinfe/semi-icons'
import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { ConsolePageHeader } from '../../components/ConsolePageHeader'
import { MetricCard } from '../../components/MetricCard'
import { RemoteState } from '../../components/RemoteState'
import {
  formatQuota,
  formatUsd,
  getPaymentOrders,
  type PaymentOrder,
  type PaymentOrderPage,
  type PaymentOrderStatus,
} from '../../api/portal'

const STATUS_COLORS: Record<PaymentOrderStatus, string> = {
  WAITING_PAYMENT: 'blue',
  CONFIRMED: 'blue',
  CREDITING: 'blue',
  PAID: 'green',
  CREDIT_FAILED: 'orange',
  CREDIT_UNKNOWN: 'orange',
  EXPIRED: 'grey',
  CANCELLED: 'grey',
}

type LoadState =
  | { kind: 'loading' }
  | { kind: 'ready'; page: PaymentOrderPage }
  | { kind: 'error'; message: string }

function formatTimestamp(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

/** 将支付后端的状态码映射为统一的颜色、文案回退值和语义图标。 */
function statusTag(status: PaymentOrderStatus): { color: string; label: string; icon: ReactNode } {
  const icon = status === 'PAID'
    ? <IconTickCircle />
    : status === 'CREDIT_FAILED' || status === 'CREDIT_UNKNOWN'
      ? <IconAlertCircle />
      : status === 'EXPIRED' || status === 'CANCELLED'
        ? <IconAlertCircle />
        : <IconClock />
  return { color: STATUS_COLORS[status], label: status, icon }
}

export function OrdersPage() {
  const { t } = useTranslation()
  const [state, setState] = useState<LoadState>({ kind: 'loading' })

  const load = async () => {
    setState({ kind: 'loading' })
    try {
      const page = await getPaymentOrders(1, 20)
      setState({ kind: 'ready', page })
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      setState({ kind: 'error', message })
    }
  }

  useEffect(() => {
    void load()
  }, [])

  if (state.kind === 'loading') {
    return (
      <main>
        <ConsolePageHeader title={t('orders.title')} description={t('orders.description')} />
        <RemoteState kind="loading" />
      </main>
    )
  }

  if (state.kind === 'error') {
    return (
      <main>
        <ConsolePageHeader title={t('orders.title')} description={t('orders.description')} />
        <RemoteState kind="error" onRetry={load} />
        <Typography.Paragraph type="tertiary">{state.message}</Typography.Paragraph>
      </main>
    )
  }

  const { items, total } = state.page

  // 订单状态由支付后端维护；页面仅将已到账和仍在处理的状态分组为摘要，保持原有数据口径。
  const completedCount = items.filter((order) => order.status === 'PAID').length
  const pendingCount = items.filter((order) => ['WAITING_PAYMENT', 'CONFIRMED', 'CREDITING'].includes(order.status)).length

  if (total === 0) {
    return (
      <main>
        <ConsolePageHeader title={t('orders.title')} description={t('orders.description')} />
        <Card>
          <Empty description={t('orders.empty')}>
            <Button
              theme="solid"
              type="primary"
              icon={<IconRefresh />}
              onClick={() => window.location.assign('/console/recharge')}
            >
              {t('orders.recharge')}
            </Button>
          </Empty>
        </Card>
      </main>
    )
  }

  const columns = [
    {
      title: t('orders.orderNo'),
      dataIndex: 'orderNo' as const,
      render: (value: string) => <code>{value}</code>,
    },
    {
      title: t('orders.amount'),
      dataIndex: 'amountUsdMinor' as const,
      render: (value: number) => formatUsd(value),
    },
    {
      title: t('orders.quota'),
      dataIndex: 'quotaToCredit' as const,
      render: (value: number) => formatQuota(value),
    },
    {
      title: t('orders.method'),
      dataIndex: 'method' as const,
      render: (value: string) => value === 'PAYPAL'
        ? <img className="payment-method-logo" src="/Paypal.png" alt="PayPal" />
        : value,
    },
    {
      title: t('orders.status'),
      dataIndex: 'status' as const,
      render: (value: PaymentOrderStatus) => {
        const meta = statusTag(value)
        return (
          <Tag color={meta.color as 'blue' | 'green' | 'orange' | 'grey'}>
            <span className="order-status-content">{meta.icon}{t(`orders.status.${value.toLowerCase()}`, { defaultValue: meta.label })}</span>
          </Tag>
        )
      },
    },
    {
      title: t('orders.created'),
      dataIndex: 'createdAt' as const,
      render: (value: string) => formatTimestamp(value),
    },
    {
      title: t('orders.expires'),
      dataIndex: 'expiresAt' as const,
      render: (value: string) => formatTimestamp(value),
    },
  ]

  return (
    <main>
      <ConsolePageHeader
        title={t('orders.title')}
        description={t('orders.description')}
        actions={
          <Space>
            <Button icon={<IconRefresh />} onClick={load}>{t('common.refresh', { defaultValue: t('common.retry') })}</Button>
          </Space>
        }
      />
      <section className="console-summary-grid" aria-label={t('orders.title')}>
        <MetricCard label={t('orders.total')} value={total} icon={<IconCreditCard />} tone="blue" />
        <MetricCard label={t('orders.completed')} value={completedCount} icon={<IconTickCircle />} tone="mint" />
        <MetricCard label={t('orders.pending')} value={pendingCount} icon={<IconClock />} tone="amber" />
      </section>
      <div className="console-table-wrap">
        <Table<PaymentOrder>
          dataSource={items}
          columns={columns}
          rowKey={(row) => row?.orderNo ?? ''}
          pagination={false}
        />
      </div>
    </main>
  )
}
