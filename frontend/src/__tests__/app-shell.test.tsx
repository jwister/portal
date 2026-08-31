import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'

import { App } from '../App'
import i18n, { LOCALE_STORAGE_KEY } from '../i18n'

describe('portal application shell', () => {
  beforeEach(async () => {
    localStorage.clear()
    await i18n.changeLanguage('en')
  })

  it('shows public navigation and the primary console action', () => {
    render(<App />)

    expect(screen.getByRole('link', { name: 'Models' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Purchase' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Console' })).toBeVisible()
  })

  it('switches language and persists the visitor preference', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: '中文' }))

    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('zh-CN')
    expect(screen.getByRole('link', { name: '模型' })).toBeVisible()
  })
})
