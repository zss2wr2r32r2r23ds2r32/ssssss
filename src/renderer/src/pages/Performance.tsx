import { useEffect, useState } from 'react'
import type { CleanupCandidate } from '../../../shared/types'
import { Toggle } from '../components/ui/Toggle'
import { api } from '../lib/api'
import { useApp } from '../store/AppState'

export function PerformancePage() {
  const { profile, setConfig, pushToast } = useApp()
  const [candidates, setCandidates] = useState<CleanupCandidate[]>([])

  useEffect(() => {
    void api.candidates().then(setCandidates)
  }, [])

  if (!profile) return null
  const p = profile.performance

  const persist = async (patch: Partial<typeof p>) => {
    setConfig(await api.updateProfile(profile.id, { performance: { ...p, ...patch } }))
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>Performance</h2>
          <p>Helpers only. Cleanup reduces background CPU/RAM — it is not FPS magic and never touches anti-cheat, GPU stacks, or Epic auth.</p>
        </div>
      </div>
      <div className="grid grid-2">
        <section className="card">
          <h3>Epic PC competitive guidance</h3>
          <p className="hint">
            Recommendations follow Epic’s publicly documented PC competitive settings. Nautical can write matching Fortnite
            config keys; it does not claim a competitive advantage.
          </p>
          <Toggle
            checked={profile.graphics.fullscreen}
            onChange={(fullscreen) => void api.updateProfile(profile.id, { graphics: { ...profile.graphics, fullscreen } }).then(setConfig)}
            label="Fullscreen"
            hint="Epic guidance: fullscreen for the most consistent frame pacing."
          />
          <Toggle
            checked={!profile.graphics.vsync}
            onChange={(off) => void api.updateProfile(profile.id, { graphics: { ...profile.graphics, vsync: !off } }).then(setConfig)}
            label="VSync off"
            hint="Epic guidance: disable VSync to avoid extra input latency."
          />
          <Toggle
            checked={profile.graphics.performanceMode}
            onChange={(performanceMode) => void api.updateProfile(profile.id, { graphics: { ...profile.graphics, performanceMode } }).then(setConfig)}
            label="Performance Mode"
            hint="Epic competitive PC settings prefer Performance Mode when available."
          />
          <Toggle
            checked={profile.graphics.lowGraphics}
            onChange={(lowGraphics) => void api.updateProfile(profile.id, { graphics: { ...profile.graphics, lowGraphics } }).then(setConfig)}
            label="Low graphics"
            hint="Lower view-distance / effects load. Visibility preference, not a hidden advantage."
          />
          <div className="field">
            <label>Frame rate cap</label>
            <select
              value={String(profile.graphics.fpsLimit)}
              onChange={(event) => {
                const value = event.target.value === 'unlimited' ? 'unlimited' : Number(event.target.value)
                void api.updateProfile(profile.id, { graphics: { ...profile.graphics, fpsLimit: value } }).then(setConfig)
              }}
            >
              <option value="unlimited">Unlimited / uncapped</option>
              <option value="240">240</option>
              <option value="160">160</option>
              <option value="144">144</option>
              <option value="120">120</option>
              <option value="60">60</option>
            </select>
            <div className="hint">Epic guidance: high or unlimited FPS for competitive PC play.</div>
          </div>
        </section>
        <section className="card">
          <h3>Gaming cleanup</h3>
          <Toggle checked={p.cleanupOnLaunch} onChange={(cleanupOnLaunch) => void persist({ cleanupOnLaunch })} label="Run selected cleanup on launch" />
          <Toggle checked={p.restoreAfterExit} onChange={(restoreAfterExit) => void persist({ restoreAfterExit })} label="Attempt restore after Fortnite exits" />
          <p className="hint">
            Protected: Explorer (unless you force it elsewhere — we never do), system/CSRSS/lsass, audio, GPU drivers,
            security/AV, Fortnite, Easy Anti-Cheat, Epic launcher/auth.
          </p>
          <div className="row" style={{ marginTop: 8 }}>
            <button
              type="button"
              className="btn"
              onClick={async () => {
                const result = await api.cleanup(p.selectedApps)
                pushToast({ tone: result.ok ? 'success' : 'warn', title: 'Cleanup', body: result.message })
                setCandidates(await api.candidates())
              }}
            >
              Run cleanup now
            </button>
            <button type="button" className="btn ghost" onClick={() => void api.restoreCleanup()}>
              Restore
            </button>
          </div>
        </section>
      </div>
      <section className="card" style={{ marginTop: 16 }}>
        <h3>Candidates</h3>
        {candidates.length === 0 ? <div className="empty">No catalog processes reported on this OS. On Windows, running apps appear here.</div> : null}
        {candidates.map((app) => (
          <label key={app.id} className="toggle">
            <div>
              <div>
                {app.name} {app.running ? `· ${app.memoryMb} MB` : '· not running'}
              </div>
              <div className="hint">{app.description}</div>
            </div>
            <input
              type="checkbox"
              disabled={app.protected}
              checked={p.selectedApps.includes(app.id)}
              onChange={(event) => {
                const selected = event.target.checked
                  ? [...p.selectedApps, app.id]
                  : p.selectedApps.filter((id) => id !== app.id)
                void persist({ selectedApps: selected })
              }}
            />
          </label>
        ))}
      </section>
    </div>
  )
}
