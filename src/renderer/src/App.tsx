import { useEffect, useState, type ReactElement } from 'react'
import { Sidebar } from './components/layout/Sidebar'
import { TitleBar } from './components/layout/TitleBar'
import { Toasts } from './components/ui/Toasts'
import { CrosshairPage } from './pages/Crosshair'
import { HomePage } from './pages/Home'
import { MacroPage } from './pages/Macro'
import { PerformancePage } from './pages/Performance'
import { ResolutionPage } from './pages/Resolution'
import { ScrimsPage } from './pages/Scrims'
import { SettingsPage } from './pages/Settings'
import { Wizard } from './pages/Wizard'
import { useApp } from './store/AppState'
import type { NavPage } from '../../shared/types'

const PAGES: Record<NavPage, () => ReactElement | null> = {
  home: HomePage,
  crosshair: CrosshairPage,
  resolution: ResolutionPage,
  performance: PerformancePage,
  macro: MacroPage,
  scrims: ScrimsPage,
  settings: SettingsPage
}

export function App() {
  const { ready, config, page, toasts, dismissToast } = useApp()
  const [visited, setVisited] = useState<NavPage[]>(['home'])

  useEffect(() => {
    setVisited((current) => (current.includes(page) ? current : [...current, page]))
  }, [page])

  if (config) {
    document.documentElement.style.setProperty('--accent', config.appearance.accent)
    document.documentElement.style.setProperty('--panel', `rgba(34, 31, 46, ${config.appearance.transparency})`)
  }

  if (!ready || !config) {
    return (
      <div className="wizard">
        <div className="hint">Starting Avix…</div>
      </div>
    )
  }

  if (!config.wizardCompleted) {
    return (
      <div className="app-root">
        <TitleBar />
        <Wizard />
        <Toasts toasts={toasts} onDismiss={dismissToast} />
      </div>
    )
  }

  return (
    <div className="app-root">
      <div className="shell">
        <TitleBar />
        <Sidebar />
        <main className="content">
          {visited.map((id) => {
            const Page = PAGES[id]
            return (
              <div key={id} className={id === page ? 'page-visible' : 'page-hidden'} hidden={id !== page}>
                <Page />
              </div>
            )
          })}
        </main>
      </div>
      <Toasts toasts={toasts} onDismiss={dismissToast} />
    </div>
  )
}
