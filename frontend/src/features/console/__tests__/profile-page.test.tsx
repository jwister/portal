import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '../../../i18n'
import { ProfilePage } from '../ProfilePage'

describe('ProfilePage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('loads safe profile fields and saves only editable profile values', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 7, username: 'alice', displayName: 'Alice', email: 'alice@example.com', language: 'en' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 7, username: 'alice', displayName: 'Alice Updated', email: 'alice@example.com', language: 'en' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    render(<ProfilePage />)

    expect(await screen.findByDisplayValue('alice')).toBeDisabled()
    expect(screen.getByDisplayValue('alice@example.com')).toBeDisabled()
    await user.clear(screen.getByLabelText('Display name'))
    await user.type(screen.getByLabelText('Display name'), 'Alice Updated')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/console/profile', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ displayName: 'Alice Updated', language: 'en' }),
    })))
  })
})
