import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '../../../i18n'
import { ModelsPage } from '../ModelsPage'

describe('ModelsPage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('zh-CN')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      items: [
        { name: 'gpt-5-mini', vendor: 'OpenAI', group: 'default', inputPrice: 1, outputPrice: 2, cachePrice: null, priceAvailable: true },
        { name: 'glm-5', vendor: 'Zhipu', group: 'default', inputPrice: null, outputPrice: null, cachePrice: null, priceAvailable: false },
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
})
