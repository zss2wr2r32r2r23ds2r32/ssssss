import { motion } from 'framer-motion'
import { useEffect, useState } from 'react'
import { api } from '../lib/api'
import { useApp } from '../store/AppState'
import { LaunchIcon } from '../components/icons/Icons'
import { ProfileBar } from '../components/profiles/ProfileBar'
import type { MonitorSnapshot } from '../../../shared/types'

export function HomePage() {
  const { config, profile, status, setConfig, pushToast } = useApp()
  const [busy, setBusy] = useState(false)
  const [monitor, setMonitor] = useState<MonitorSnapshot | null>(null)

  useEffect(() => {
    if (status !== 'RUNNING') return
    let alive = true
    const tick = async () => {
      try {
        const snap = await api.monitor()
        if (alive) setMonitor(snap)
      } catch {
        // Optional telemetry.
      }
    }
    void tick()
    const id = setInterval(() => void tick(), 2500)
    return () => {
      alive = false
      clearInterval(id)
    }
  }, [status])

  if (!config || !profile) return null

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
      <ProfileBar />
      <div className="grid grid-2" style={{ marginTop: 18 }}>
        <motion.section className="card" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
          <div className="hint">Competitive session</div>
          <h2 className="hero-word">Fortnite</h2>
          <p className="hero-sub">Ready to compete?</p>
          <motion.button
            type="button"
            className="launch"
            whileHover={{ scale: 1.015 }}
            whileTap={{ scale: 0.985 }}
            disabled={busy || status === 'LAUNCHING' || status === 'CLOSING'}
            onClick={() => void launch()}
          >
            <LaunchIcon /> {busy || status === 'LAUNCHING' ? 'LAUNCHING…' : 'LAUNCH FORTNITE'}
          </motion.button>
          <p className="hint" style={{ marginTop: 12 }}>
            Applies the active profile’s Fortnite-only settings, optional cleanup, then starts your configured executable.
            Temporary display/config changes restore when Fortnite closes.
          </p>
        </motion.section>
        <section className="grid">
          <div className="card stat">
            <span>Install</span>
            <strong>{config.fortnitePath ? 'Detected' : 'Not found'}</strong>
            <div className="hint">{config.fortnitePath ?? 'Browse to Fortnite.exe — Nautical never assumes a hardcoded path.'}</div>
            <div className="row" style={{ marginTop: 10 }}>
              <button
                type="button"
                className="btn"
                onClick={async () => {
                  const info = await api.detectFortnite()
                  setConfig(await api.getConfig())
                  pushToast({
                    tone: info.valid ? 'success' : 'warn',
                    title: 'Fortnite path',
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
                  setConfig(await api.getConfig())
                  pushToast({
                    tone: info.valid ? 'success' : 'warn',
                    title: 'Browse',
                    body: info.valid ? `Using ${info.path}` : info.reason
                  })
                }}
              >
                Browse
              </button>
            </div>
          </div>
          <div className="card stat">
            <span>Active profile</span>
            <strong>{profile.name}</strong>
            <div className="hint">
              {profile.resolution.width}×{profile.resolution.height} · Crosshair {profile.crosshair.enabled ? 'on' : 'off'} · Macro{' '}
              {profile.macro.enabled ? 'armed' : 'off'}
            </div>
          </div>
        </section>
      </div>
      <div className="grid grid-4" style={{ marginTop: 16 }}>
        <div className="card stat">
          <span>Display / stretch</span>
          <strong>
            {profile.resolution.width}×{profile.resolution.height}
          </strong>
          <div className="hint">{profile.resolution.method} · {profile.resolution.temporary ? 'temporary' : 'saved'}</div>
        </div>
        <div className="card stat">
          <span>Crosshair</span>
          <strong>{profile.crosshair.enabled ? profile.crosshair.presetId.replace(/-/g, ' ') : 'Disabled'}</strong>
          <div className="hint">{profile.crosshair.onlyWhileFortnite ? 'Only while Fortnite is running' : 'Always available'}</div>
        </div>
        <div className="card stat">
          <span>Macro</span>
          <strong>{profile.macro.enabled ? `${profile.macro.key} / ${profile.macro.intervalSec.toFixed(2)}s` : 'Off'}</strong>
          <div className="hint">{profile.macro.mode.replace('-', ' ')}</div>
        </div>
        <div className="card stat">
          <span>Version</span>
          <strong>{config.fortniteVersion ?? 'Unknown'}</strong>
          <div className="hint">Read from the executable when Windows exposes it.</div>
        </div>
      </div>
      {status === 'RUNNING' && monitor ? (
        <div className="card" style={{ marginTop: 16 }}>
          <h3>Session monitor</h3>
          <p className="hint">External OS counters only. Nautical does not inject into Fortnite.</p>
          <div className="grid grid-4" style={{ marginTop: 12 }}>
            <div className="stat">
              <span>CPU</span>
              <strong>{monitor.cpuLoad ?? '—'}%</strong>
            </div>
            <div className="stat">
              <span>RAM</span>
              <strong>
                {monitor.ramUsedMb && monitor.ramTotalMb
                  ? `${Math.round((monitor.ramUsedMb / monitor.ramTotalMb) * 100)}%`
                  : '—'}
              </strong>
            </div>
            <div className="stat">
              <span>GPU</span>
              <strong>{monitor.gpuLoad != null ? `${monitor.gpuLoad}%` : 'n/a'}</strong>
            </div>
            <div className="stat">
              <span>Temps</span>
              <strong>{monitor.cpuTemp != null ? `${monitor.cpuTemp}°C` : 'n/a'}</strong>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
