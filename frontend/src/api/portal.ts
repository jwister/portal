export interface DashboardSummary {
  availableQuota: number
  usedQuota: number
  requestCount: number
  tokenUsage: number | null
}

export interface TokenSummary {
  id: number
  name: string
  enabled: boolean
  remainingQuota: number
  usedQuota: number
  unlimited: boolean
  expiredTime: number
  maskedKey: string
}

export interface TokenWriteRequest {
  name: string
  unlimited: boolean
  remainingQuota: number
  expiredTime: number
}

export interface ModelCatalogItem {
  name: string
  vendor: string
  group: string
  inputPrice: number | null
  outputPrice: number | null
  cachePrice: number | null
  priceAvailable: boolean
}

export class PortalApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message)
    this.name = 'PortalApiError'
  }
}

async function request(path: string, init?: RequestInit): Promise<Response> {
  let response: Response
  try {
    response = await fetch(path, { ...init, credentials: 'include' })
  } catch {
    throw new PortalApiError('Unable to reach the portal.', 0)
  }

  if (response.ok) return response

  let message = 'Unable to complete the request.'
  try {
    const body = await response.json() as { message?: unknown }
    if (typeof body.message === 'string' && body.message.trim()) message = body.message
  } catch {
    // Fall back to the safe local message for non-JSON errors.
  }
  throw new PortalApiError(message, response.status)
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await request(path, init)
  return response.json() as Promise<T>
}

export function getDashboard(): Promise<DashboardSummary> {
  return requestJson('/api/console/dashboard')
}

export async function getTokens(): Promise<TokenSummary[]> {
  const body = await requestJson<{ items: TokenSummary[] }>('/api/console/tokens')
  return body.items
}

export async function createToken(token: TokenWriteRequest): Promise<void> {
  await request('/api/console/tokens', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(token),
  })
}

export function updateToken(id: number, token: TokenWriteRequest): Promise<TokenSummary> {
  return requestJson(`/api/console/tokens/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(token),
  })
}

export function setTokenEnabled(id: number, enabled: boolean): Promise<TokenSummary> {
  return requestJson(`/api/console/tokens/${id}/status`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
}

export async function deleteToken(id: number): Promise<void> {
  await request(`/api/console/tokens/${id}`, { method: 'DELETE' })
}

export function getTokenKey(id: number): Promise<{ key: string }> {
  return requestJson(`/api/console/tokens/${id}/key`)
}

export async function getModelCatalog(): Promise<ModelCatalogItem[]> {
  const body = await requestJson<{ items: ModelCatalogItem[] }>('/api/catalog/models')
  return body.items
}
