import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { SignUpPage } from '../SignUpPage'

describe('SignUpPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('posts a new email account to the portal registration endpoint', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    const onRegistered = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    render(<SignUpPage onRegistered={onRegistered} />)
    await user.type(screen.getByLabelText('Username'), 'alice')
    await user.type(screen.getByLabelText('Email'), 'alice@example.com')
    await user.type(screen.getByLabelText('Password'), 'password')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/register', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ username: 'alice', email: 'alice@example.com', password: 'password' }),
    }))
    expect(onRegistered).toHaveBeenCalledOnce()
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/sign-in')
  })
})
