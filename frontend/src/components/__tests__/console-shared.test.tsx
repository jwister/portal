import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import '../../i18n'
import { ConsolePageHeader } from '../ConsolePageHeader'
import { MetricCard } from '../MetricCard'
import { RemoteState } from '../RemoteState'

describe('console shared components', () => {
  it('renders accessible loading, error retry, and empty states', async () => {
    const user = userEvent.setup()
    const retry = vi.fn()
    const { rerender } = render(<RemoteState kind="loading" />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading…')

    rerender(<RemoteState kind="error" onRetry={retry} />)
    expect(screen.getByRole('alert')).toHaveTextContent('Unable to load data.')
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(retry).toHaveBeenCalledOnce()

    rerender(<RemoteState kind="empty" />)
    expect(screen.getByText('No data available.')).toBeVisible()
  })

  it('renders a reusable header and metric card', () => {
    render(
      <>
        <ConsolePageHeader title="Console overview" description="Account activity at a glance." />
        <MetricCard label="Available quota" value="1,200" hint="Updated now" />
      </>,
    )

    expect(screen.getByRole('heading', { name: 'Console overview' })).toBeVisible()
    expect(screen.getByText('Account activity at a glance.')).toBeVisible()
    expect(screen.getByText('Available quota')).toBeVisible()
    expect(screen.getByText('1,200')).toBeVisible()
    expect(screen.getByText('Updated now')).toBeVisible()
  })
})
