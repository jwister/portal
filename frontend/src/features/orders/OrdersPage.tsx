import { Button, Card, Empty, Space, Table, Tag, Typography } from '@douyinfe/semi-ui'
import { IconRefresh } from '@douyinfe/semi-icons'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { ConsolePageHeader } from '../../components/ConsolePageHeader'
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

function statusTag(status: PaymentOrderStatus): { color: string; label: string } {
  return { color: STATUS_COLORS[status], label: status }
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
      title: t('orders.status'),
      dataIndex: 'status' as const,
      render: (value: PaymentOrderStatus) => {
        const meta = statusTag(value)
        return (
          <Tag color={meta.color as 'blue' | 'green' | 'orange' | 'grey'}>
            {t(`orders.status.${value.toLowerCase()}`, { defaultValue: meta.label })}
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