import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'

import i18n from '../../../i18n'
import { HomePage } from '../HomePage'

describe('HomePage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('zh-CN')
  })

  it('renders the API hero, onboarding steps, and API-key action', () => {
    render(<HomePage />)

    expect(screen.getByRole('heading', { name: '一个 API，连接海量 AI 模型' })).toBeVisible()
    expect(screen.getByText('创建账户')).toBeVisible()
    expect(screen.getByText('充值余额')).toBeVisible()
    expect(screen.getByText('调用 API')).toBeVisible()
    expect(screen.getByRole('link', { name: '获取 API Key' })).toHaveAttribute('href', '/sign-in')
  })
})
