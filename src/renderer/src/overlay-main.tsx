import { useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { DEFAULT_CROSSHAIR } from '../../shared/defaults'
import type { CrosshairSettings } from '../../shared/types'
import { CrosshairMark } from './components/crosshair/CrosshairMark'

function OverlayApp() {
  const [settings, setSettings] = useState<CrosshairSettings>(DEFAULT_CROSSHAIR)

  useEffect(() => {
    const api = window.nauticalOverlay
    if (!api) return
    return api.onSettings((payload) => {
      setSettings(payload as CrosshairSettings)
    })
  }, [])

  if (!settings.enabled && settings.onlyWhileFortnite) {
    // Overlay window is only created when needed; still render the mark.
  }

  return (
    <div style={{ width: '100%', height: '100%', display: 'grid', placeItems: 'center' }}>
      <CrosshairMark settings={settings} size={220} />
    </div>
  )
}

createRoot(document.getElementById('overlay')!).render(<OverlayApp />)
