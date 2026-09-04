import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '../../../i18n'
import { ModelsPage } from '../ModelsPage'

describe('ModelsPage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('zh-CN')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      items: [
        { name: 'gpt-5-mini', vendor: 'OpenAI', groups: ['default', 'premium'], inputPrice: 1, outputPrice: 2, cachePrice: null, priceAvailable: true },
        { name: 'glm-5', vendor: 'Zhipu', groups: ['standard'], inputPrice: null, outputPrice: null, cachePrice: null, priceAvailable: false },
      ],
    }), { status: 200 })))
  })

  it('filters model cards by model name', async () => {
    const user = userEvent.setup()
    render(<ModelsPage />)

    await screen.findByText('gpt-5-mini')
    await user.type(screen.getByPlaceholderText('搜索模型'), 'glm')

    expect(screen.getByText('glm-5')).toBeVisible()
    expect(screen.queryByText('gpt-5-mini')).not.toBeInTheDocument()
  })

  it('exposes the refreshed catalog hierarchy and card sections', async () => {
    render(<ModelsPage />)

    expect(await screen.findByText('gpt-5-mini')).toBeVisible()
    expect(screen.getByTestId('models-hero-copy')).toBeVisible()
    expect(screen.getByTestId('models-summary')).toBeVisible()
    expect(within(screen.getByTestId('models-hero-copy')).getByText('模型目录')).toBeVisible()
    expect(screen.getByTestId('model-card-gpt-5-mini')).toHaveAttribute('data-layout', 'catalog')
    expect(screen.getByTestId('model-card-gpt-5-mini-pricing')).toBeVisible()
  })

  it('filters models by selected group and keeps search scoped to that group', async () => {
    const user = userEvent.setup()
    render(<ModelsPage />)

    await screen.findByText('gpt-5-mini')
    const groupNav = screen.getByRole('navigation', { name: '模型分组' })
    const premium = within(groupNav).getByRole('button', { name: /premium/ })
    expect(premium).toHaveAttribute('aria-pressed', 'false')
    await user.click(premium)

    expect(premium).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('gpt-5-mini')).toBeVisible()
    expect(screen.queryByText('glm-5')).not.toBeInTheDocument()

    await user.click(within(groupNav).getByRole('button', { name: /standard/ }))
    expect(screen.getByText('glm-5')).toBeVisible()
    expect(screen.queryByText('gpt-5-mini')).not.toBeInTheDocument()
    await user.type(screen.getByPlaceholderText('搜索模型'), 'gpt')
    expect(screen.queryByText('glm-5')).not.toBeInTheDocument()
    expect(screen.getByText('没有找到模型')).toBeVisible()
  })
})
