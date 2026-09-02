import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'

import i18n from '../../../i18n'
import { OrdersPage } from '../OrdersPage'

describe('OrdersPage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
  })

  it('shows an honest empty state instead of fabricated orders', () => {
    render(<OrdersPage />)

    expect(screen.getByRole('heading', { name: 'Orders' })).toBeVisible()
    expect(screen.getByText('Order management is coming soon.')).toBeVisible()
    expect(screen.getByRole('button', { name: /Go to recharge/ })).toBeVisible()
  })
})
