export interface AuthProfile {
  id: number
  username: string
}

export interface AuthStatus {
  authenticated: boolean
  profile: AuthProfile | null
}

export interface OAuthProviderStatus {
  githubEnabled: boolean
  githubClientId: string
  oidcEnabled: boolean
  oidcClientId: string
  oidcAuthorizationEndpoint: string
  oidcDisplayName: string
}

export interface OAuthCallbackPayload {
  code: string | null
  state: string | null
  error: string | null
  errorDescription: string | null
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

/** 获取仅用于构造授权跳转的公开 OAuth 配置，不会向浏览器暴露第三方 secret。 */
export async function getOAuthProviders(): Promise<OAuthProviderStatus> {
  const response = await request('/api/auth/oauth/providers')
  return await response.json() as OAuthProviderStatus
}

/** 向 Portal BFF 获取 NewAPI 管理的一次性 OAuth state。 */
export async function createOAuthState(provider: 'github' | 'oidc'): Promise<string> {
  const response = await request(`/api/auth/oauth/${provider}/state`, { method: 'POST' })
  const body = await response.json() as { state?: unknown }
  if (typeof body.state !== 'string' || !body.state) throw new AuthApiError(genericError, response.status)
  return body.state
}

/** 回调信息只经 BFF 转发，浏览器永远不会接触 NewAPI access token。 */
export async function completeOAuth(provider: 'github' | 'oidc', payload: OAuthCallbackPayload): Promise<void> {
  await request(`/api/auth/oauth/${provider}/complete`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

/** 生成 GitHub OAuth 授权地址。 */
export function buildGitHubOAuthUrl(clientId: string, state: string): string {
  const url = new URL('https://github.com/login/oauth/authorize')
  url.searchParams.set('client_id', clientId)
  url.searchParams.set('state', state)
  url.searchParams.set('scope', 'user:email')
  return url.toString()
}

/** 生成 Google OIDC 授权地址，并固定回到 Portal 的同源 callback 路径。 */
export function buildOidcOAuthUrl(authorizationEndpoint: string, clientId: string, state: string): string {
  const url = new URL(authorizationEndpoint)
  url.searchParams.set('client_id', clientId)
  url.searchParams.set('redirect_uri', `${window.location.origin}/oauth/oidc`)
  url.searchParams.set('response_type', 'code')
  url.searchParams.set('scope', 'openid profile email')
  url.searchParams.set('state', state)
  return url.toString()
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
