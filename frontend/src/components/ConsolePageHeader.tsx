import { Typography } from '@douyinfe/semi-ui'
import type { ReactNode } from 'react'

interface ConsolePageHeaderProps {
  title: string
  description?: string
  actions?: ReactNode
}

export function ConsolePageHeader({ title, description, actions }: ConsolePageHeaderProps) {
  return (
    <header className="console-page-header">
      <div>
        <Typography.Title heading={2}>{title}</Typography.Title>
        {description && <Typography.Paragraph type="tertiary">{description}</Typography.Paragraph>}
      </div>
      {actions && <div className="console-page-header-actions">{actions}</div>}
    </header>
  )
}
