export interface DashboardSummary {
  availableQuota: number
  usedQuota: number
  requestCount: number
}

export async function getDashboard(): Promise<DashboardSummary> {
  const response = await fetch('/api/console/dashboard', { credentials: 'include' })
  if (!response.ok) {
    throw new Error('Dashboard request failed')
  }
  return response.json() as Promise<DashboardSummary>
}
