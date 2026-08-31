import { DashboardPage } from './features/console/DashboardPage'
import { SignInPage } from './features/auth/SignInPage'
import { SignUpPage } from './features/auth/SignUpPage'
import { TokensPage } from './features/console/TokensPage'
import { PublicHeader } from './components/PublicHeader'
import { HomePage } from './features/home/HomePage'
import { ModelsPage } from './features/catalog/ModelsPage'

export function App() {
  if (window.location.pathname === '/console/dashboard') {
    return <DashboardPage />
  }

  if (window.location.pathname === '/sign-in') {
    return <SignInPage onAuthenticated={() => window.location.assign('/console/dashboard')} />
  }

  if (window.location.pathname === '/sign-up') {
    return <SignUpPage onRegistered={() => window.location.assign('/sign-in')} />
  }

  if (window.location.pathname === '/console/tokens') {
    return <TokensPage />
  }

  if (window.location.pathname === '/models') {
    return <><PublicHeader /><ModelsPage /></>
  }

  return <><PublicHeader /><HomePage /></>
}
