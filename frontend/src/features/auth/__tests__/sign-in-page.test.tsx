import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { SignInPage } from '../SignInPage'

describe('SignInPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('posts entered credentials to the portal login endpoint', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    render(<SignInPage onAuthenticated={vi.fn()} />)
    await user.type(screen.getByLabelText('Username'), 'alice@example.com')
    await user.type(screen.getByLabelText('Password'), 'password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/sign-in', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ username: 'alice@example.com', password: 'password' }),
    }))
    expect(screen.getByRole('link', { name: 'Create account' })).toHaveAttribute('href', '/sign-up')
  })

  it('rejects an invalid email before submitting', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    render(<SignInPage />)
    await user.type(screen.getByLabelText('Username'), 'not-an-email')
    await user.type(screen.getByLabelText('Password'), 'password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(fetchMock).not.toHaveBeenCalled()
    expect(screen.getByText('Please enter a valid email address.')).toBeInTheDocument()
  })

  it('shows a server-provided error message when sign-in fails', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ message: 'Unable to verify sign-in details' }),
      { status: 401, headers: { 'Content-Type': 'application/json' } },
    ))
    vi.stubGlobal('fetch', fetchMock)
    render(<SignInPage onAuthenticated={vi.fn()} />)
    await user.type(screen.getByLabelText('Username'), 'alice@example.com')
    await user.type(screen.getByLabelText('Password'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    const messages = await screen.findAllByText('Unable to verify sign-in details')
    expect(messages.length).toBeGreaterThanOrEqual(1)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('keeps the submit button busy while the request is pending', async () => {
    let resolveFetch: (response: Response) => void = () => undefined
    const fetchMock = vi.fn(() => new Promise<Response>((resolve) => { resolveFetch = resolve }))
    vi.stubGlobal('fetch', fetchMock)
    render(<SignInPage onAuthenticated={vi.fn()} />)
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Username'), 'alice@example.com')
    await user.type(screen.getByLabelText('Password'), 'password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    const submitButton = screen.getByRole('button', { name: 'Sign in' })
    expect(fetchMock).toHaveBeenCalled()
    // Semi renders a loading button with a spin icon and loading class while awaiting.
    expect(submitButton).toHaveClass('semi-button-loading')
    resolveFetch(new Response(null, { status: 204 }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Sign in' })).not.toHaveClass('semi-button-loading'))
  })
})
