import { AnimatePresence, motion } from 'framer-motion'
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

const PAGES = {
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

  const Page = PAGES[page]
  return (
    <div className="app-root">
      <div className="shell">
        <TitleBar />
        <Sidebar />
        <main className="content">
          <AnimatePresence mode="wait">
            <motion.div
              key={page}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -6 }}
              transition={{ duration: config.appearance.animationIntensity === 'off' ? 0 : 0.22 }}
            >
              <Page />
            </motion.div>
          </AnimatePresence>
        </main>
      </div>
      <Toasts toasts={toasts} onDismiss={dismissToast} />
    </div>
  )
}
