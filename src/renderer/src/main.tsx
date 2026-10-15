import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import { AppProvider } from './store/AppState'
import './styles/global.css'

function isElectronRenderer(): boolean {
  return typeof navigator !== 'undefined' && /Electron/i.test(navigator.userAgent)
}

async function boot(): Promise<void> {
  // Never install the browser mock inside Electron — packaged builds must use the preload bridge.
  if (import.meta.env.DEV && !isElectronRenderer() && !window.nautical) {
    const { installMockBridge } = await import('./lib/mock-bridge')
    installMockBridge()
  }

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <AppProvider>
        <App />
      </AppProvider>
    </StrictMode>
  )
}

void boot()
