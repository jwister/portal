import { Button, Empty, Spin, Typography } from '@douyinfe/semi-ui'
import { useTranslation } from 'react-i18next'

import '../i18n'

export type RemoteStateKind = 'loading' | 'error' | 'empty'

interface RemoteStateProps {
  kind: RemoteStateKind
  onRetry?: () => void
}

export function RemoteState({ kind, onRetry }: RemoteStateProps) {
  const { t } = useTranslation()

  if (kind === 'loading') {
    return (
      <div className="remote-state" role="status" aria-live="polite">
        <Spin />
        <Typography.Text>{t('common.loading')}</Typography.Text>
      </div>
    )
  }

  if (kind === 'error') {
    return (
      <div className="remote-state" role="alert">
        <Typography.Text type="danger">{t('common.loadError')}</Typography.Text>
        {onRetry && <Button theme="solid" type="primary" onClick={onRetry}>{t('common.retry')}</Button>}
      </div>
    )
  }

  return (
    <div className="remote-state">
      <Empty description={t('common.empty')} />
    </div>
  )
}
