import { useEffect, useState } from 'react'
import banner from '../assets/home-banner.png'
import { LaunchIcon } from '../components/icons/Icons'
import { api } from '../lib/api'
import { useApp } from '../store/AppState'

export function HomePage() {
  const { config, profile, status, setConfig, setPage, pushToast } = useApp()
  const [busy, setBusy] = useState(false)
  const [avatar, setAvatar] = useState<string | null>(null)

  useEffect(() => {
    if (!config?.avatar.fileName) {
      setAvatar(null)
      return
    }
    void api.getAvatar().then(setAvatar)
  }, [config?.avatar.fileName])

  if (!config || !profile) return null
  const name = config.general.displayName || 'competitor'
  const launching = busy || status === 'LAUNCHING' || status === 'CLOSING'
  const running = status === 'RUNNING'

  const launch = async () => {
    setBusy(true)
    try {
      const result = await api.launch()
      pushToast({
        tone: result.ok ? (result.code ? 'warn' : 'success') : 'error',
        title: result.ok ? 'Launch' : 'Could not launch',
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
          <div className={`skin-portrait ${avatar ? '' : 'placeholder'}`}>
            {avatar ? <img src={avatar} alt={name} /> : name.slice(0, 1).toUpperCase()}
          </div>
          <div className="hero-copy">
            <h2>What's up, {name}?</h2>
            <p>Your photo, your session. Avix starts Fortnite through Epic so the game can stay signed in.</p>
            <div className="row" style={{ marginTop: 12 }}>
              <button
                type="button"
                className="btn"
                onClick={async () => {
                  const result = await api.importAvatar()
                  if (result.data) {
                    setAvatar(result.data.image)
                    setConfig(await api.getConfig())
                  }
                  pushToast({ tone: result.ok ? 'success' : 'warn', title: 'Profile picture', body: result.message })
                }}
              >
                Change photo
              </button>
              <button type="button" className="btn" onClick={() => setPage('settings')}>
                Settings
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
            {profile.resolution.width}×{profile.resolution.height} · Launch via {config.general.launchMethod ?? 'epic'}
          </div>
          <div className="row" style={{ marginTop: 10 }}>
            <button type="button" className="btn" onClick={() => setPage('scrims')}>
              Scrim alerts
            </button>
            <button
              type="button"
              className="btn"
              onClick={async () => {
                const result = await api.checkUpdates()
                pushToast({
                  tone: result.newer ? 'warn' : result.ok ? 'info' : 'error',
                  title: result.newer ? 'Update available' : 'Updates',
                  body: result.message
                })
                if (result.newer && result.downloadUrl) {
                  await api.openUpdates(result.downloadUrl)
                }
              }}
            >
              Check updates
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
        <button type="button" className="launch" style={{ flex: 1 }} disabled={launching || running} onClick={() => void launch()}>
          <LaunchIcon /> {running ? 'FORTNITE RUNNING' : launching ? 'LAUNCHING…' : 'LAUNCH FORTNITE'}
        </button>
      </div>
    </div>
  )
}
