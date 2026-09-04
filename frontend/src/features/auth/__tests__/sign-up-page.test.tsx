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
    const fetchMock = vi.fn((path: string) => path === '/api/auth/captcha'
      ? Promise.resolve(new Response(JSON.stringify({ captchaId: 'cap-1', image: 'data:image/png;base64,x', expiresIn: 300 }), { status: 200 }))
      : Promise.resolve(new Response(null, { status: 204 })))
    const onRegistered = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    render(<SignUpPage onRegistered={onRegistered} />)
    await user.type(screen.getByLabelText('Username'), 'alice')
    await user.type(screen.getByLabelText('Email'), 'alice@example.com')
    await user.type(screen.getByLabelText('Password'), 'password')
    await user.type(screen.getByLabelText('Confirm password'), 'password')
    await user.click(screen.getByRole('button', { name: 'Send code' }))
    await screen.findByRole('dialog')
    await user.type(screen.getByLabelText('Image captcha'), 'ABCDE')
    await user.click(screen.getByRole('button', { name: 'Verify and send' }))
    await user.type(screen.getByLabelText('Email verification code'), '123456')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/sign-up', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ username: 'alice', email: 'alice@example.com', password: 'password', verificationCode: '123456' }),
    }))
    expect(screen.getByRole('button', { name: /Retry in/ })).toBeDisabled()
    expect(onRegistered).toHaveBeenCalledOnce()
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/sign-in')
  })

  it('does not submit when the email is malformed', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn((path: string) => path === '/api/auth/captcha'
      ? Promise.resolve(new Response(JSON.stringify({ captchaId: 'cap-1', image: 'data:image/png;base64,x', expiresIn: 300 }), { status: 200 }))
      : Promise.resolve(new Response(null, { status: 204 })))
    vi.stubGlobal('fetch', fetchMock)
    render(<SignUpPage />)
    await user.type(screen.getByLabelText('Username'), 'alice')
    await user.type(screen.getByLabelText('Email'), 'not-an-email')
    await user.type(screen.getByLabelText('Password'), 'password')
    await user.type(screen.getByLabelText('Confirm password'), 'password')
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(fetchMock.mock.calls.filter(([path]) => path !== '/api/auth/captcha')).toHaveLength(0)
    expect(screen.getByText('Please enter a valid email address.')).toBeInTheDocument()
  })

  it('does not submit when passwords do not match', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn((path: string) => path === '/api/auth/captcha'
      ? Promise.resolve(new Response(JSON.stringify({ captchaId: 'cap-1', image: 'data:image/png;base64,x', expiresIn: 300 }), { status: 200 }))
      : Promise.resolve(new Response(null, { status: 204 })))
    vi.stubGlobal('fetch', fetchMock)
    render(<SignUpPage />)
    await user.type(screen.getByLabelText('Username'), 'alice')
    await user.type(screen.getByLabelText('Email'), 'alice@example.com')
    await user.type(screen.getByLabelText('Password'), 'password')
    await user.type(screen.getByLabelText('Confirm password'), 'different')
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(fetchMock.mock.calls.filter(([path]) => path !== '/api/auth/captcha')).toHaveLength(0)
    expect(screen.getByText('Passwords do not match.')).toBeInTheDocument()
  })
})
