import { DashboardPage } from './features/console/DashboardPage'
import { SignInPage } from './features/auth/SignInPage'
import { SignUpPage } from './features/auth/SignUpPage'
import { TokensPage } from './features/console/TokensPage'
import { LogsPage } from './features/console/LogsPage'
import { ProfilePage } from './features/console/ProfilePage'
import { OrdersPage } from './features/orders/OrdersPage'
import { PurchasePage } from './features/payments/PurchasePage'
import { RechargePage } from './features/payments/RechargePage'
import { PublicHeader } from './components/PublicHeader'
import { useEffect, type ReactNode } from 'react'
import { useAuthStatus } from './auth/use-auth-status'
import { RemoteState } from './components/RemoteState'
import { ConsoleLayout, type ConsoleKey } from './components/ConsoleLayout'
import { HomePage } from './features/home/HomePage'
import { ModelsPage } from './features/catalog/ModelsPage'
import './i18n'

function ConsoleRoute({ activeKey, children }: { activeKey: ConsoleKey, children: ReactNode }) {
  const status = useAuthStatus()

  useEffect(() => {
    if (status.kind !== 'anonymous') return
    const returnTo = `${window.location.pathname}${window.location.search}`
    window.location.assign(`/sign-in?returnTo=${encodeURIComponent(returnTo)}`)
  }, [status])

  if (status.kind === 'loading') return <RemoteState kind="loading" />
  if (status.kind === 'anonymous') return null
  return <ConsoleLayout activeKey={activeKey}>{children}</ConsoleLayout>
}

export function App() {
  const path = window.location.pathname
  if (path === '/console/dashboard') return <ConsoleRoute activeKey="dashboard"><DashboardPage /></ConsoleRoute>
  if (path === '/console/tokens') return <ConsoleRoute activeKey="tokens"><TokensPage /></ConsoleRoute>
  if (path === '/console/recharge') return <ConsoleRoute activeKey="recharge"><RechargePage /></ConsoleRoute>
  if (path === '/console/logs') return <ConsoleRoute activeKey="logs"><LogsPage /></ConsoleRoute>
  if (path === '/console/profile') return <ConsoleRoute activeKey="profile"><ProfilePage /></ConsoleRoute>
  if (path === '/console/orders') return <ConsoleRoute activeKey="orders"><OrdersPage /></ConsoleRoute>
  if (path === '/sign-in') return <SignInPage onAuthenticated={() => window.location.assign('/console/dashboard')} />
  if (path === '/sign-up') return <SignUpPage onRegistered={() => window.location.assign('/sign-in')} />
  if (path === '/models') return <><PublicHeader /><ModelsPage /></>
  if (path === '/purchase') return <><PublicHeader /><PurchasePage /></>
  if (path === '/') return <><PublicHeader /><HomePage /></>
  return <><PublicHeader /><main className="models-page"><h1>Not Found</h1></main></>
}
