import { StrictMode } from 'react'
import '@douyinfe/semi-ui/react19-adapter'
import { createRoot } from 'react-dom/client'


import { App } from './App'
import './i18n'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
