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
import { useEffect, useState, type ReactNode } from 'react'
import { useAuthStatus } from './auth/use-auth-status'
import { RemoteState } from './components/RemoteState'
import { ConsoleLayout, type ConsoleKey } from './components/ConsoleLayout'
import { HomePage } from './features/home/HomePage'
import { ModelsPage } from './features/catalog/ModelsPage'
import './i18n'

function ConsoleRoute({ activeKey, children, onNavigate }: { activeKey: ConsoleKey, children: ReactNode, onNavigate: (path: string) => void }) {
  const status = useAuthStatus()

  useEffect(() => {
    if (status.kind !== 'anonymous') return
    const returnTo = `${window.location.pathname}${window.location.search}`
    window.location.assign(`/sign-in?returnTo=${encodeURIComponent(returnTo)}`)
  }, [status])

  if (status.kind === 'loading') return <RemoteState kind="loading" />
  if (status.kind === 'anonymous') return null
  return <ConsoleLayout activeKey={activeKey} onNavigate={onNavigate}><div className="console-route-content" key={activeKey}>{children}</div></ConsoleLayout>
}

export function App() {
  const [path, setPath] = useState(() => window.location.pathname)

  useEffect(() => {
    const handlePopState = () => setPath(window.location.pathname)
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  const navigateConsole = (nextPath: string) => {
    if (nextPath === path) return
    window.history.pushState({}, '', nextPath)
    setPath(nextPath)
  }

  if (path === '/console/dashboard') return <ConsoleRoute activeKey="dashboard" onNavigate={navigateConsole}><DashboardPage /></ConsoleRoute>
  if (path === '/console/tokens') return <ConsoleRoute activeKey="tokens" onNavigate={navigateConsole}><TokensPage /></ConsoleRoute>
  if (path === '/console/recharge') return <ConsoleRoute activeKey="recharge" onNavigate={navigateConsole}><RechargePage /></ConsoleRoute>
  if (path === '/console/logs') return <ConsoleRoute activeKey="logs" onNavigate={navigateConsole}><LogsPage /></ConsoleRoute>
  if (path === '/console/profile') return <ConsoleRoute activeKey="profile" onNavigate={navigateConsole}><ProfilePage /></ConsoleRoute>
  if (path === '/console/orders') return <ConsoleRoute activeKey="orders" onNavigate={navigateConsole}><OrdersPage /></ConsoleRoute>
  if (path === '/sign-in') return <SignInPage />
  if (path === '/sign-up') return <SignUpPage onRegistered={() => window.location.assign('/sign-in')} />
  if (path === '/models') return <><PublicHeader /><ModelsPage /></>
  if (path === '/purchase') return <><PublicHeader /><PurchasePage /></>
  if (path === '/') return <><PublicHeader /><HomePage /></>
  return <><PublicHeader /><main className="models-page"><h1>Not Found</h1></main></>
}
