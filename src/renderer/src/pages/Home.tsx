import { motion } from 'framer-motion'
import { useEffect, useState } from 'react'
import type { SkinPreview } from '../../../shared/types'
import banner from '../assets/home-banner.png'
import { LaunchIcon } from '../components/icons/Icons'
import { api } from '../lib/api'
import { useApp } from '../store/AppState'

export function HomePage() {
  const { config, profile, status, setConfig, setPage, pushToast } = useApp()
  const [busy, setBusy] = useState(false)
  const [skin, setSkin] = useState<SkinPreview | null>(config?.skin ?? null)

  useEffect(() => {
    void api.detectSkin().then((next) => {
      setSkin(next)
    })
  }, [])

  if (!config || !profile) return null
  const name = config.general.displayName || 'competitor'

  const launch = async () => {
    setBusy(true)
    try {
      const result = await api.launch()
      pushToast({
        tone: result.ok ? (result.code ? 'warn' : 'success') : 'error',
        title: result.ok ? 'Launch sequence' : 'Could not launch',
        body: result.message
      })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <section className="hero-banner" style={{ backgroundImage: `url(${banner})` }}>
        <div className="hero-banner-inner">
          <div className={`skin-portrait ${skin?.image ? '' : 'placeholder'}`}>
            {skin?.image ? <img src={skin.image} alt={skin.name ?? 'Skin'} /> : 'A'}
          </div>
          <div className="hero-copy">
            <h2>What's up, {name}?</h2>
            <p>
              {skin?.name
                ? `Last used look: ${skin.name}${skin.source === 'log' ? ' (from local Fortnite logs)' : ''}`
                : 'Last used skin isn’t in local files — upload a preview if you want it here.'}
            </p>
            <div className="row" style={{ marginTop: 12 }}>
              <button type="button" className="btn" onClick={() => void api.detectSkin().then(setSkin)}>
                Refresh skin
              </button>
              <button
                type="button"
                className="btn"
                onClick={async () => {
                  const result = await api.importSkin()
                  if (result.data) setSkin(result.data)
                  if (result.data) setConfig(await api.getConfig())
                  pushToast({ tone: result.ok ? 'success' : 'warn', title: 'Skin preview', body: result.message })
                }}
              >
                Upload preview
              </button>
            </div>
          </div>
        </div>
        <div className="wave" />
      </section>

      <div className="grid grid-2" style={{ marginTop: 16 }}>
        <div className="card stat">
          <span>Install</span>
          <strong>{config.fortnitePath ? 'Ready' : 'Not found'}</strong>
          <div className="hint">{config.fortnitePath ?? 'Detect or browse to FortniteClient-Win64-Shipping.exe.'}</div>
          <div className="row" style={{ marginTop: 10 }}>
            <button
              type="button"
              className="btn"
              onClick={async () => {
                const info = await api.detectFortnite()
                setConfig(await api.getConfig())
                pushToast({
                  tone: info.valid ? 'success' : 'error',
                  title: info.valid ? 'Fortnite found' : 'Fortnite not found',
                  body: info.valid ? `Found ${info.path}` : info.reason
                })
              }}
            >
              Detect
            </button>
            <button
              type="button"
              className="btn"
              onClick={async () => {
                const info = await api.browseFortnite()
                if (info.reason === 'Browse cancelled.') return
                setConfig(await api.getConfig())
                pushToast({
                  tone: info.valid ? 'success' : 'error',
                  title: info.valid ? 'Fortnite path set' : 'Invalid Fortnite executable',
                  body: info.valid ? `Using ${info.path}` : info.reason
                })
              }}
            >
              Browse
            </button>
          </div>
        </div>
        <div className="card stat">
          <span>Session</span>
          <strong>{profile.name}</strong>
          <div className="hint">
            {profile.resolution.width}×{profile.resolution.height} · Crosshair {profile.crosshair.enabled ? 'on' : 'off'} · Macro{' '}
            {profile.macro.enabled ? 'armed' : 'off'}
          </div>
          <div className="row" style={{ marginTop: 10 }}>
            <button type="button" className="btn" onClick={() => setPage('scrims')}>
              Scrim alerts
            </button>
            <button type="button" className="btn" onClick={() => setPage('settings')}>
              Settings
            </button>
          </div>
        </div>
      </div>

      <div className="grid grid-3" style={{ marginTop: 14 }}>
        <button type="button" className="card stat" onClick={() => setPage('crosshair')}>
          <span>Crosshair</span>
          <strong>{profile.crosshair.enabled ? 'Armed' : 'Off'}</strong>
          <div className="hint">{profile.crosshair.presetId}</div>
        </button>
        <button type="button" className="card stat" onClick={() => setPage('resolution')}>
          <span>Resolution</span>
          <strong>
            {profile.resolution.width}×{profile.resolution.height}
          </strong>
          <div className="hint">{profile.resolution.method}</div>
        </button>
        <button type="button" className="card stat" onClick={() => setPage('performance')}>
          <span>Performance</span>
          <strong>Live meters</strong>
          <div className="hint">CPU, RAM, GPU — no game injection</div>
        </button>
      </div>

      <div className="row" style={{ marginTop: 16 }}>
        <motion.button
          type="button"
          className="launch"
          style={{ flex: 1 }}
          whileHover={{ scale: 1.01 }}
          whileTap={{ scale: 0.99 }}
          disabled={busy || status === 'LAUNCHING' || status === 'CLOSING'}
          onClick={() => void launch()}
        >
          <LaunchIcon /> {busy || status === 'LAUNCHING' ? 'LAUNCHING…' : 'LAUNCH FORTNITE'}
        </motion.button>
      </div>
    </div>
  )
}
