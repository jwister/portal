import { Card, Typography } from '@douyinfe/semi-ui'

interface MetricCardProps {
  label: string
  value: string | number
  hint?: string
}

export function MetricCard({ label, value, hint }: MetricCardProps) {
  return (
    <Card className="metric-card">
      <Typography.Text type="tertiary">{label}</Typography.Text>
      <Typography.Title heading={3}>{value}</Typography.Title>
      {hint && <Typography.Text type="tertiary">{hint}</Typography.Text>}
    </Card>
  )
}
