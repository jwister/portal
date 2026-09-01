import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { ConsoleLayout } from '../ConsoleLayout'

describe('ConsoleLayout', () => {
  it('renders readable console navigation with the active item selected', () => {
    render(<ConsoleLayout activeKey="dashboard"><div>content</div></ConsoleLayout>)

    const navigation = screen.getByRole('navigation', { name: 'Console navigation' })
    expect(navigation).toBeVisible()
    expect(within(navigation).getByText('仪表盘')).toBeVisible()
    expect(within(navigation).getByText('令牌管理')).toBeVisible()
    expect(within(navigation).getByText('仪表盘').closest('[role="menuitem"]')).toHaveClass('semi-navigation-item-selected')
  })
})
