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

export interface TokenPage {
  page: number
  pageSize: number
  total: number
  items: TokenSummary[]
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
  groups: string[]
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

  if (response.status === 401 && window.location.pathname.startsWith('/console')) {
    const returnTo = `${window.location.pathname}${window.location.search}`
    window.location.assign(`/sign-in?returnTo=${encodeURIComponent(returnTo)}`)
  }

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

export function getTokens(page = 1, pageSize = 50): Promise<TokenPage> {
  return requestJson(`/api/console/tokens${queryString({ page, pageSize })}`)
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

export interface LogEntry {
  id: number
  createdAt: number
  type: number
  content: string
  tokenName: string
  modelName: string
  quota: number
  promptTokens: number
  completionTokens: number
  useTime: number
  stream: boolean
  requestId: string
}

export interface LogPage {
  page: number
  pageSize: number
  total: number
  items: LogEntry[]
}

export interface LogStats {
  quota: number
  rpm: number
  tpm: number
}

export interface LogQuery {
  page: number
  pageSize: number
  startTimestamp?: number
  endTimestamp?: number
  modelName?: string
  tokenName?: string
  type?: number
}

export interface Profile {
  id: number
  username: string
  displayName: string
  email: string
  language: string | null
}

export interface ProfileUpdateRequest {
  displayName?: string
  language?: string
}

function queryString(query: object): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query) as Array<[string, string | number | undefined]>) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  const value = params.toString()
  return value ? `?${value}` : ''
}

export function getLogs(query: LogQuery): Promise<LogPage> {
  return requestJson(`/api/console/logs${queryString(query)}`)
}

export function getLogStats(query: Omit<LogQuery, 'page' | 'pageSize'>): Promise<LogStats> {
  return requestJson(`/api/console/logs/stats${queryString(query)}`)
}

export function getProfile(): Promise<Profile> {
  return requestJson('/api/console/profile')
}

export function updateProfile(profile: ProfileUpdateRequest): Promise<Profile> {
  return requestJson('/api/console/profile', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(profile),
  })
}

export async function getModelCatalog(): Promise<ModelCatalogItem[]> {
  const body = await requestJson<{ items: ModelCatalogItem[] }>('/api/catalog/models')
  return body.items
}

export type PaymentMethod = 'PAYPAL'

export type PaymentOrderStatus =
  | 'WAITING_PAYMENT'
  | 'CONFIRMED'
  | 'CREDITING'
  | 'PAID'
  | 'CREDIT_FAILED'
  | 'CREDIT_UNKNOWN'
  | 'EXPIRED'
  | 'CANCELLED'

export interface PaymentOrder {
  orderNo: string
  amountUsdMinor: number
  quotaToCredit: number
  method: PaymentMethod
  status: PaymentOrderStatus
  expiresAt: string
  confirmedAt: string | null
  creditedAt: string | null
  createdAt: string
}

export interface PaymentOrderPage {
  items: PaymentOrder[]
  page: number
  pageSize: number
  total: number
}

export interface PayPalConfig {
  clientId: string
  mode: 'sandbox' | 'live'
}

export interface PayPalProviderOrder {
  providerOrderId: string
}

export interface CreatePaymentOrderInput {
  amount: string
  method: PaymentMethod
}

export function createPaymentOrder(input: CreatePaymentOrderInput): Promise<PaymentOrder> {
  return requestJson<PaymentOrder>('/api/payments/orders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount: input.amount, method: input.method }),
  })
}

export function getPaymentOrder(orderNo: string): Promise<PaymentOrder> {
  return requestJson<PaymentOrder>(`/api/payments/orders/${encodeURIComponent(orderNo)}`)
}

export function getPaymentOrders(page = 1, pageSize = 20): Promise<PaymentOrderPage> {
  return requestJson<PaymentOrderPage>(`/api/payments/orders${queryString({ page, pageSize })}`)
}

export function getPayPalConfig(orderNo: string): Promise<PayPalConfig> {
  return requestJson<PayPalConfig>(`/api/payments/orders/${encodeURIComponent(orderNo)}/paypal/config`)
}

export function createPayPalProviderOrder(orderNo: string): Promise<PayPalProviderOrder> {
  return requestJson<PayPalProviderOrder>(`/api/payments/orders/${encodeURIComponent(orderNo)}/paypal/order`, {
    method: 'POST',
  })
}

export function capturePayPalOrder(orderNo: string): Promise<PaymentOrder> {
  return requestJson<PaymentOrder>(`/api/payments/orders/${encodeURIComponent(orderNo)}/paypal/capture`, {
    method: 'POST',
  })
}

export function formatUsd(amountUsdMinor: number): string {
  const dollars = (amountUsdMinor / 100).toFixed(2)
  return `$${dollars}`
}

export function formatQuota(quota: number): string {
  return quota.toLocaleString('en-US')
}
