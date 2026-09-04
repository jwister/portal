export interface AuthProfile {
  id: number
  username: string
}

export interface AuthStatus {
  authenticated: boolean
  profile: AuthProfile | null
}

export interface CaptchaResponse { captchaId: string; image: string; expiresIn: number }

export class AuthApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'AuthApiError'
    this.status = status
  }
}

const genericError = 'The authentication request could not be completed.'

async function request(path: string, init?: RequestInit): Promise<Response> {
  let response: Response
  try {
    response = await fetch(path, { ...init, credentials: 'include' })
  } catch {
    throw new AuthApiError(genericError, 0)
  }
  if (response.ok) return response

  let message = genericError
  try {
    const body = await response.json() as { message?: unknown }
    if (typeof body.message === 'string' && body.message.trim()) message = body.message
  } catch {
    // Use the safe generic message when the response is not JSON.
  }
  throw new AuthApiError(message, response.status)
}

export async function signIn(username: string, password: string): Promise<void> {
  await request('/api/auth/sign-in', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
}

export async function signUp(username: string, email: string, password: string, verificationCode = ''): Promise<void> {
  await request('/api/auth/sign-up', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, email, password, verificationCode }),
  })
}

export async function getCaptcha(): Promise<CaptchaResponse> {
  const response = await request('/api/auth/captcha')
  return await response.json() as CaptchaResponse
}

export async function sendEmailVerification(email: string, captchaId: string, captchaCode: string): Promise<void> {
  await request(`/api/auth/verification?email=${encodeURIComponent(email)}&captchaId=${encodeURIComponent(captchaId)}&captchaCode=${encodeURIComponent(captchaCode)}`)
}

export async function getAuthStatus(): Promise<AuthStatus> {
  const response = await request('/api/auth/status')
  return await response.json() as AuthStatus
}

export async function signOut(): Promise<void> {
  await request('/api/auth/sign-out', { method: 'POST' })
}

export function getSafeReturnTo(value: string | null | undefined, fallback = '/console/dashboard'): string {
  if (!value || !value.startsWith('/') || value.startsWith('//')) return fallback
  try {
    const url = new URL(value, window.location.origin)
    if (url.origin !== window.location.origin) return fallback
    return `${url.pathname}${url.search}${url.hash}`
  } catch {
    return fallback
  }
}

/** @deprecated Use getAuthStatus. Kept for existing consumers. */
export async function getCurrentProfile(): Promise<AuthProfile | null> {
  const status = await getAuthStatus()
  return status.authenticated ? status.profile : null
}
