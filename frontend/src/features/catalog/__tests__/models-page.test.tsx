import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '../../../i18n'
import { ModelsPage } from '../ModelsPage'

describe('ModelsPage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('zh-CN')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: true,
      data: [
        { id: 1, model_name: 'gpt-5-mini', vendor_id: 7, enable_groups: ['default', 'premium'], model_ratio: 1, completion_ratio: 2, quota_type: 0 },
        { id: 2, model_name: 'glm-5', vendor_id: 8, enable_groups: ['standard'], model_price: 0.5, completion_ratio: 2, quota_type: 1 },
      ],
      vendors: [{ id: 7, name: 'OpenAI' }, { id: 8, name: 'Zhipu' }],
      group_ratio: { default: 1, premium: 1.5, standard: 1 },
      usable_group: { default: 'default', premium: 'premium', standard: 'standard' },
      supported_endpoint: {},
      auto_groups: [],
      pricing_version: 'v42',
    }), { status: 200 })))
  })

  it('filters model cards by model name', async () => {
    const user = userEvent.setup()
    render(<ModelsPage />)

    await screen.findByText('gpt-5-mini')
    expect(fetch).toHaveBeenCalledWith('/api/catalog/pricing', { credentials: 'include' })
    expect(screen.getByText('OpenAI')).toBeVisible()
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

  it('renders the Quiet Ledger catalog status and a labelled results region', async () => {
    render(<ModelsPage />)

    expect(await screen.findByText('gpt-5-mini')).toBeVisible()
    expect(screen.getByTestId('models-ledger-status')).toHaveTextContent('实时目录')
    expect(screen.getByRole('region', { name: '模型目录' })).toBeVisible()
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
