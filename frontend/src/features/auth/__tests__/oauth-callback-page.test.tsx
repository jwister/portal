import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { OAuthCallbackPage } from '../OAuthCallbackPage'

describe('OAuthCallbackPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    window.history.replaceState({}, '', '/')
  })

  it('completes the provider callback before entering the console', async () => {
    const onAuthenticated = vi.fn()
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    window.history.replaceState({}, '', '/oauth/github?code=provider-code&state=flow-token')

    render(<OAuthCallbackPage provider="github" onAuthenticated={onAuthenticated} />)

    await waitFor(() => expect(onAuthenticated).toHaveBeenCalledOnce())
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/oauth/github/complete', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ code: 'provider-code', state: 'flow-token', error: null, errorDescription: null }),
    }))
  })

  it('does not submit malformed callbacks', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    window.history.replaceState({}, '', '/oauth/github?state=flow-token')

    render(<OAuthCallbackPage provider="github" />)

    expect(await screen.findByRole('alert')).toHaveTextContent('OAuth sign-in could not be completed.')
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
