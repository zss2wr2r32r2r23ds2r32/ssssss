import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import { installMockBridge } from './lib/mock-bridge'
import { AppProvider } from './store/AppState'
import './styles/global.css'

installMockBridge()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppProvider>
      <App />
    </AppProvider>
  </StrictMode>
)
