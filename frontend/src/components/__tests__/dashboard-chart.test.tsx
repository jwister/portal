import { render, screen } from '@testing-library/react'
import type { EChartsOption } from 'echarts'
import { describe, expect, it } from 'vitest'

import { DashboardChart } from '../DashboardChart'

describe('DashboardChart', () => {
  it('provides the chart values as readable text for assistive technology', () => {
    render(
      <DashboardChart
        title="Usage trend"
        accessibleDescription="2026-09-05: quota 70, requests 7"
        option={{} as EChartsOption}
      />,
    )

    expect(screen.getByRole('region', { name: 'Usage trend' })).toHaveTextContent('2026-09-05: quota 70, requests 7')
  })
})
