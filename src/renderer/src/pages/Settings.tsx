import { useEffect, useState } from 'react'
import { APP_NAME, APP_VERSION, type LaunchMethod } from '../../../shared/types'
import { ProfileBar } from '../components/profiles/ProfileBar'
import { Toggle } from '../components/ui/Toggle'
import { api } from '../lib/api'
import { useApp } from '../store/AppState'

export function SettingsPage() {
  const { config, profile, setConfig, pushToast } = useApp()
  const [avatar, setAvatar] = useState<string | null>(null)
  const [configPath, setConfigPath] = useState<string>('')
  const [updateNote, setUpdateNote] = useState<string>('')

  useEffect(() => {
    void api.appInfo().then((info) => setConfigPath(info.configPath))
  }, [])

  useEffect(() => {
    if (!config?.avatar.fileName) {
      setAvatar(null)
      return
    }
    void api.getAvatar().then(setAvatar)
  }, [config?.avatar.fileName])

  if (!config || !profile) return null
  const g = config.general
  const a = config.appearance

  const applyGeneral = async (patch: Partial<typeof g>) => {
    try {
      setConfig(await api.applyGeneral({ ...g, launchMethod: g.launchMethod ?? 'epic', ...patch }))
    } catch (error) {
      pushToast({
        tone: 'error',
        title: 'Could not save settings',
        body: error instanceof Error ? error.message : 'Write failed.'
      })
    }
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>Settings</h2>
          <p>Saved to disk on every change, and again when Avix quits. Portable and installed builds share this folder.</p>
        </div>
      </div>
      <section className="card" style={{ marginBottom: 16 }}>
        <h3>Profile picture</h3>
        <div className="row">
          <div className={`skin-portrait ${avatar ? '' : 'placeholder'}`} style={{ width: 72, height: 72 }}>
            {avatar ? <img src={avatar} alt="Profile" /> : (g.displayName || 'A').slice(0, 1).toUpperCase()}
          </div>
          <button
            type="button"
            className="btn primary"
            onClick={async () => {
              const result = await api.importAvatar()
              if (result.data) {
                setAvatar(result.data.image)
                setConfig(await api.getConfig())
              }
              pushToast({ tone: result.ok ? 'success' : 'warn', title: 'Profile picture', body: result.message })
            }}
          >
            Upload photo
          </button>
          <button
            type="button"
            className="btn"
            onClick={async () => {
              await api.clearAvatar()
              setAvatar(null)
              setConfig(await api.getConfig())
            }}
          >
            Remove
          </button>
        </div>
      </section>
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
              defaultValue={g.displayName}
              onBlur={(event) => void applyGeneral({ displayName: event.target.value.trim() || 'competitor' })}
            />
          </div>
          <div className="field">
            <label>How to start Fortnite</label>
            <select
              value={g.launchMethod ?? 'epic'}
              onChange={(event) => void applyGeneral({ launchMethod: event.target.value as LaunchMethod })}
            >
              <option value="epic">Epic Games Launcher / URI (recommended)</option>
              <option value="bootstrapper">FortniteBootstrapper.exe</option>
              <option value="shipping">Shipping.exe only (often exits without Epic)</option>
            </select>
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
            <input defaultValue={g.discordUrl} onBlur={(event) => void applyGeneral({ discordUrl: event.target.value })} />
          </div>
          <div className="field">
            <label>Scrim Discord webhook (optional)</label>
            <input
              type="password"
              autoComplete="off"
              placeholder="https://discord.com/api/webhooks/…"
              defaultValue={g.discordWebhookUrl}
              onBlur={(event) => void applyGeneral({ discordWebhookUrl: event.target.value })}
            />
            <div className="hint">Stored only in your local avix-config.json. Never hardcoded.</div>
          </div>
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
          <h3 style={{ marginTop: 22 }}>Updates</h3>
          <p className="hint">Current version {APP_VERSION}. Checks GitHub releases for {`zss2wr2r32r2r23ds2r32/ssssss`}.</p>
          {updateNote ? <p className="hint">{updateNote}</p> : null}
          <div className="row" style={{ marginTop: 10 }}>
            <button
              type="button"
              className="btn primary"
              onClick={async () => {
                const result = await api.checkUpdates()
                setUpdateNote(result.message)
                pushToast({
                  tone: result.newer ? 'warn' : result.ok ? 'info' : 'error',
                  title: result.newer ? 'Update available' : `${APP_NAME} ${result.current}`,
                  body: result.message
                })
              }}
            >
              Check for updates
            </button>
            <button
              type="button"
              className="btn"
              onClick={async () => {
                const result = await api.checkUpdates()
                await api.openUpdates(result.downloadUrl ?? result.releasesUrl)
              }}
            >
              Open download page
            </button>
          </div>
        </section>
      </div>
      <p className="hint" style={{ marginTop: 18 }}>
        {APP_NAME} {APP_VERSION} writes {configPath || 'avix-config.json'} and flushes it on quit. No cheats, no injection.
      </p>
    </div>
  )
}
