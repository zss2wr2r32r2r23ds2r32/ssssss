import { useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { DEFAULT_CROSSHAIR } from '../../shared/defaults'
import { overlayMarkSize } from '../../shared/overlay-center'
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

  const size = overlayMarkSize(settings.size)
  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <CrosshairMark settings={settings} size={size} />
    </div>
  )
}

createRoot(document.getElementById('overlay')!).render(<OverlayApp />)
