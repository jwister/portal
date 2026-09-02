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
import { ConsoleLayout } from './components/ConsoleLayout'
import { HomePage } from './features/home/HomePage'
import { ModelsPage } from './features/catalog/ModelsPage'
import './i18n'

export function App() {
  const path = window.location.pathname
  if (path === '/console/dashboard') return <ConsoleLayout activeKey="dashboard"><DashboardPage /></ConsoleLayout>
  if (path === '/console/tokens') return <ConsoleLayout activeKey="tokens"><TokensPage /></ConsoleLayout>
  if (path === '/console/recharge') return <ConsoleLayout activeKey="recharge"><RechargePage /></ConsoleLayout>
  if (path === '/console/logs') return <ConsoleLayout activeKey="logs"><LogsPage /></ConsoleLayout>
  if (path === '/console/profile') return <ConsoleLayout activeKey="profile"><ProfilePage /></ConsoleLayout>
  if (path === '/console/orders') return <ConsoleLayout activeKey="orders"><OrdersPage /></ConsoleLayout>
  if (path === '/sign-in') return <SignInPage onAuthenticated={() => window.location.assign('/console/dashboard')} />
  if (path === '/sign-up') return <SignUpPage onRegistered={() => window.location.assign('/sign-in')} />
  if (path === '/models') return <><PublicHeader /><ModelsPage /></>
  if (path === '/purchase') return <><PublicHeader /><PurchasePage /></>
  if (path === '/') return <><PublicHeader /><HomePage /></>
  return <><PublicHeader /><main className="models-page"><h1>Not Found</h1></main></>
}
