import { APP_NAME, APP_VERSION } from '../../../shared/types'
import { ProfileBar } from '../components/profiles/ProfileBar'
import { Toggle } from '../components/ui/Toggle'
import { api } from '../lib/api'
import { useApp } from '../store/AppState'

export function SettingsPage() {
  const { config, profile, setConfig, pushToast } = useApp()
  if (!config || !profile) return null
  const g = config.general
  const a = config.appearance

  const applyGeneral = (patch: Partial<typeof g>) =>
    api.applyGeneral({ ...g, ...patch }).then(setConfig)

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>Settings</h2>
          <p>Local JSON config, profiles, and appearance. Nothing here phones home except a version check you opt into.</p>
        </div>
      </div>
      <section className="card" style={{ marginBottom: 16 }}>
        <h3>Profiles</h3>
        <ProfileBar />
      </section>
      <div className="grid grid-2">
        <section className="card">
          <h3>General</h3>
          <div className="field">
            <label>Display name</label>
            <input
              maxLength={32}
              value={g.displayName}
              onChange={(event) => void applyGeneral({ displayName: event.target.value || 'competitor' })}
            />
          </div>
          <Toggle
            checked={g.startWithWindows}
            onChange={(startWithWindows) => void applyGeneral({ startWithWindows })}
            label="Start with Windows"
          />
          <Toggle
            checked={g.trayEnabled}
            onChange={(trayEnabled) => void applyGeneral({ trayEnabled })}
            label="Show tray icon"
          />
          <Toggle
            checked={g.closeToTray}
            onChange={(closeToTray) => void applyGeneral({ closeToTray })}
            label="Close button hides to tray"
            hint="Off by default so the title-bar X quits Avix."
          />
          <Toggle
            checked={g.startMinimized}
            onChange={(startMinimized) => void applyGeneral({ startMinimized })}
            label="Start minimized"
          />
          <Toggle
            checked={g.autoLaunchFortnite}
            onChange={(autoLaunchFortnite) => void applyGeneral({ autoLaunchFortnite })}
            label="Auto-launch Fortnite after Avix starts"
          />
          <Toggle
            checked={g.checkUpdates}
            onChange={(checkUpdates) => void applyGeneral({ checkUpdates })}
            label="Check for updates on start"
          />
          <Toggle
            checked={g.hardwareAcceleration}
            onChange={(hardwareAcceleration) => void applyGeneral({ hardwareAcceleration })}
            label="Hardware acceleration"
            hint="Takes effect the next time Avix starts."
          />
          <div className="field">
            <label>Discord URL</label>
            <input
              value={g.discordUrl}
              onChange={(event) => void applyGeneral({ discordUrl: event.target.value })}
            />
          </div>
          <div className="field">
            <label>Scrim Discord webhook (optional)</label>
            <input
              type="password"
              autoComplete="off"
              placeholder="https://discord.com/api/webhooks/…"
              value={g.discordWebhookUrl}
              onChange={(event) => void applyGeneral({ discordWebhookUrl: event.target.value })}
            />
            <div className="hint">Stored only in your local avix-config.json. Never hardcoded. Leave blank to toast in-app only.</div>
          </div>
          <button
            type="button"
            className="btn"
            onClick={async () => {
              const result = await api.checkUpdates()
              pushToast({ tone: 'info', title: `${APP_NAME} ${result.current}`, body: result.message })
            }}
          >
            Check updates
          </button>
        </section>
        <section className="card">
          <h3>Appearance</h3>
          <div className="field">
            <label>Theme</label>
            <select value={a.theme} disabled>
              <option value="dark">Dark (Avix)</option>
            </select>
          </div>
          <div className="field">
            <label>Accent</label>
            <input
              type="color"
              value={a.accent}
              onChange={(event) => {
                document.documentElement.style.setProperty('--accent', event.target.value)
                void api.updateConfig({ appearance: { ...a, accent: event.target.value } }).then(setConfig)
              }}
            />
          </div>
          <div className="field">
            <label>Panel transparency {Math.round(a.transparency * 100)}%</label>
            <input
              type="range"
              min={0.55}
              max={1}
              step={0.01}
              value={a.transparency}
              onChange={(event) => {
                const transparency = Number(event.target.value)
                document.documentElement.style.setProperty('--panel', `rgba(34, 31, 46, ${transparency})`)
                void api.updateConfig({ appearance: { ...a, transparency } }).then(setConfig)
              }}
            />
          </div>
          <div className="field">
            <label>Animation intensity</label>
            <select
              value={a.animationIntensity}
              onChange={(event) =>
                void api
                  .updateConfig({ appearance: { ...a, animationIntensity: event.target.value as typeof a.animationIntensity } })
                  .then(setConfig)
              }
            >
              <option value="full">Full</option>
              <option value="subtle">Subtle</option>
              <option value="off">Off</option>
            </select>
          </div>
        </section>
      </div>
      <div className="grid grid-3" style={{ marginTop: 16 }}>
        <section className="card">
          <h3>Crosshair</h3>
          <p className="hint">Enabled: {profile.crosshair.enabled ? 'yes' : 'no'} · {profile.crosshair.presetId}</p>
        </section>
        <section className="card">
          <h3>Resolution</h3>
          <p className="hint">
            {profile.resolution.width}×{profile.resolution.height} · {profile.resolution.method}
          </p>
        </section>
        <section className="card">
          <h3>Macro</h3>
          <p className="hint">
            {profile.macro.enabled ? `${profile.macro.key} @ ${profile.macro.intervalSec}s` : 'Disabled'}
          </p>
        </section>
      </div>
      <p className="hint" style={{ marginTop: 18 }}>
        {APP_NAME} {APP_VERSION} stores human-readable JSON in the app user-data folder. No cheats, no injection, documented
        Windows APIs only.
      </p>
    </div>
  )
}
