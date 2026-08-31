export interface DashboardSummary {
  availableQuota: number
  usedQuota: number
  requestCount: number
}

export interface TokenSummary {
  id: number
  name: string
  enabled: boolean
  remainingQuota: number
}

export async function getDashboard(): Promise<DashboardSummary> {
  const response = await fetch('/api/console/dashboard', { credentials: 'include' })
  if (!response.ok) {
    throw new Error('Dashboard request failed')
  }
  return response.json() as Promise<DashboardSummary>
}

export async function getTokens(): Promise<TokenSummary[]> {
  const response = await fetch('/api/console/tokens', { credentials: 'include' })
  if (!response.ok) {
    throw new Error('Token request failed')
  }
  const body = await response.json() as { items: TokenSummary[] }
  return body.items
}
