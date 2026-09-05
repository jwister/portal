import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '../../../i18n'
import { DashboardPage, getDisplayModelName, isLatestDashboardRequest } from '../DashboardPage'

// 本文件关注仪表盘数据竞态；用可检查 option 的轻量图表替身，避免 Canvas 实现掩盖数据断言。
vi.mock('../../../components/DashboardChart', () => ({
  DashboardChart: ({ option, title }: { option: unknown, title: string }) => (
    <section aria-label={title} data-option={JSON.stringify(option)} role="region" />
  ),
}))

interface DeferredResponse {
  promise: Promise<Response>
  reject: (reason?: unknown) => void
  resolve: (response: Response) => void
}

/** 创建可由测试精确控制完成顺序的网络响应，用于复现慢请求乱序返回。 */
function deferredResponse(): DeferredResponse {
  let resolve!: (response: Response) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<Response>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200 })
}

/** 读取趋势图替身收到的 ECharts option，以验证真实图表数据而非仅验证页面标题。 */
function getTrendOption(): { xAxis: { data: string[] }, series: Array<{ data: number[] }> } {
  const rawOption = screen.getByRole('region', { name: '7-day usage trend' }).getAttribute('data-option')
  if (!rawOption) throw new Error('趋势图未收到配置')
  return JSON.parse(rawOption) as { xAxis: { data: string[] }, series: Array<{ data: number[] }> }
}

const analytics30 = { dailyUsage: [{ date: '2026-09-01', quota: 300, requestCount: 30 }], topModels: [{ modelName: 'gpt-4o', quota: 300 }], tokenUsage: [{ date: '2026-09-01', tokenUsage: 3000 }] }
const analytics7 = { dailyUsage: [{ date: '2026-09-05', quota: 70, requestCount: 7 }], topModels: [{ modelName: 'gpt-4o', quota: 70 }], tokenUsage: [{ date: '2026-09-05', tokenUsage: 700 }] }
const summary30 = { availableQuota: 300, usedQuota: 30, requestCount: 30, tokenUsage: 3000 }
const summary7 = { availableQuota: 700, usedQuota: 70, requestCount: 7, tokenUsage: 700 }

describe('DashboardPage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders four account metrics and labels unavailable token usage honestly', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const path = String(input)
      if (path.includes('/analytics')) {
        return Promise.resolve(new Response(JSON.stringify({
          dailyUsage: [{ date: '2026-09-01', quota: 90, requestCount: 12 }],
          topModels: [{ modelName: 'gpt-4o', quota: 90 }],
          tokenUsage: [{ date: '2026-09-01', tokenUsage: 1200 }],
        }), { status: 200 }))
      }
      return Promise.resolve(new Response(JSON.stringify({
        availableQuota: 900,
        usedQuota: 100,
        requestCount: 12,
        tokenUsage: null,
      }), { status: 200 }))
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<DashboardPage />)

    expect(await screen.findByText('900')).toBeVisible()
    expect(screen.getByText('100')).toBeVisible()
    expect(screen.getByText('12')).toBeVisible()
    expect(screen.getByText('Token usage')).toBeVisible()
    expect(screen.getByText('Not available')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Console overview' })).toBeVisible()
    expect(await screen.findByRole('button', { name: '7 days' })).toBeVisible()
    expect(screen.getByRole('region', { name: '30-day usage trend' })).toBeVisible()
    expect(screen.getByRole('region', { name: 'Top 5 models by quota' })).toBeVisible()
    expect(screen.getByRole('region', { name: 'Last 7 days token usage' })).toBeVisible()
    expect(fetchMock).toHaveBeenCalledWith('/api/console/dashboard/analytics?range=30d', expect.anything())
  })

  it('does not allow an out-of-order analytics response to overwrite the selected 7-day range', () => {
    expect(isLatestDashboardRequest(2, 3, '7d', '7d')).toBe(false)
    expect(isLatestDashboardRequest(3, 3, '30d', '7d')).toBe(false)
    expect(isLatestDashboardRequest(3, 3, '7d', '7d')).toBe(true)
  })

  it('maps the backend other-model sentinel through the active locale label', () => {
    expect(getDisplayModelName('__other__', 'Other models')).toBe('Other models')
    expect(getDisplayModelName('gpt-4o', 'Other models')).toBe('gpt-4o')
  })

  it('keeps the 7-day chart when the deferred 30-day analytics response resolves afterwards', async () => {
    const user = userEvent.setup()
    const staleAnalytics = deferredResponse()
    const sevenSummary = deferredResponse()
    const sevenAnalytics = deferredResponse()
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const path = String(input)
      if (path === '/api/console/dashboard/analytics?range=30d') return staleAnalytics.promise
      if (path === '/api/console/dashboard/analytics?range=7d') return sevenAnalytics.promise
      if (path === '/api/console/dashboard') {
        return fetchMock.mock.calls.filter(([request]) => String(request) === path).length === 1
          ? Promise.resolve(jsonResponse(summary30))
          : sevenSummary.promise
      }
      throw new Error(`未预期的请求：${path}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<DashboardPage />)

    await user.click(await screen.findByRole('button', { name: '7 days' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/console/dashboard/analytics?range=7d', expect.anything()))
    await act(async () => {
      sevenSummary.resolve(jsonResponse(summary7))
      sevenAnalytics.resolve(jsonResponse(analytics7))
    })
    expect((await screen.findAllByText('700')).length).toBeGreaterThan(0)
    expect(screen.getByRole('region', { name: '7-day usage trend' })).toBeVisible()
    expect(getTrendOption().xAxis.data).toEqual(['09-05'])
    expect(getTrendOption().series[0].data).toEqual([70])

    await act(async () => staleAnalytics.resolve(jsonResponse(analytics30)))
    expect(screen.getAllByText('700').length).toBeGreaterThan(0)
    expect(screen.getByRole('region', { name: '7-day usage trend' })).toBeVisible()
    expect(getTrendOption().xAxis.data).toEqual(['09-05'])
    expect(getTrendOption().series[0].data).toEqual([70])
  })

  it('keeps the 7-day chart when the deferred 30-day analytics response rejects afterwards', async () => {
    const user = userEvent.setup()
    const staleAnalytics = deferredResponse()
    const sevenSummary = deferredResponse()
    const sevenAnalytics = deferredResponse()
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const path = String(input)
      if (path === '/api/console/dashboard/analytics?range=30d') return staleAnalytics.promise
      if (path === '/api/console/dashboard/analytics?range=7d') return sevenAnalytics.promise
      if (path === '/api/console/dashboard') {
        return fetchMock.mock.calls.filter(([request]) => String(request) === path).length === 1
          ? Promise.resolve(jsonResponse(summary30))
          : sevenSummary.promise
      }
      throw new Error(`未预期的请求：${path}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<DashboardPage />)

    await user.click(await screen.findByRole('button', { name: '7 days' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/console/dashboard/analytics?range=7d', expect.anything()))
    await act(async () => {
      sevenSummary.resolve(jsonResponse(summary7))
      sevenAnalytics.resolve(jsonResponse(analytics7))
    })
    expect((await screen.findAllByText('700')).length).toBeGreaterThan(0)

    await act(async () => staleAnalytics.reject(new Error('过期的 30 天请求失败')))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('region', { name: '7-day usage trend' })).toBeVisible()
    expect(getTrendOption().xAxis.data).toEqual(['09-05'])
    expect(getTrendOption().series[0].data).toEqual([70])
  })
})
