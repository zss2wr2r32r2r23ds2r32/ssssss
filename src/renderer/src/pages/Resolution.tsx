import { useEffect, useState } from 'react'
import { RESOLUTION_PRESETS, type GpuInfo, type ResolutionSettings } from '../../../shared/types'
import { api } from '../lib/api'
import { useApp } from '../store/AppState'
import { Toggle } from '../components/ui/Toggle'

export function ResolutionPage() {
  const { profile, page, setConfig, pushToast } = useApp()
  const [gpu, setGpu] = useState<{ gpu: GpuInfo; guidance: string[] } | null>(null)
  const [customW, setCustomW] = useState(1920)
  const [customH, setCustomH] = useState(1080)

  useEffect(() => {
    if (page !== 'resolution' || gpu) return
    void api.gpu().then(setGpu)
  }, [page, gpu])

  useEffect(() => {
    if (profile) {
      setCustomW(profile.resolution.width)
      setCustomH(profile.resolution.height)
    }
  }, [profile?.id])

  if (!profile) return null
  const r = profile.resolution

  const persist = async (patch: Partial<ResolutionSettings>) => {
    setConfig(await api.updateProfile(profile.id, { resolution: { ...r, ...patch } }))
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>Resolution</h2>
          <p>
            Avix changes the Windows display mode for this Fortnite session (temporary desktop resolution), then
            restores native when the game exits. It does not attach to Fortnite. Epic competitive play is 16:9.
          </p>
        </div>
        <button type="button" className="btn danger" onClick={() => void api.restoreNative()}>
          Emergency Restore Native
        </button>
      </div>
      <div className="grid grid-2">
        <section className="card">
          <h3>Presets</h3>
          <div className="grid" style={{ gridTemplateColumns: '1fr 1fr', gap: 8 }}>
            {RESOLUTION_PRESETS.map((preset) => (
              <button
                key={preset.id}
                type="button"
                className={`preset ${r.width === preset.width && r.height === preset.height ? 'active' : ''}`}
                onClick={() => {
                  setCustomW(preset.width)
                  setCustomH(preset.height)
                  void persist({ width: preset.width, height: preset.height })
                }}
              >
                <div>
                  <strong>{preset.label}</strong>
                  <div className="hint">{preset.note}</div>
                </div>
              </button>
            ))}
          </div>
          <div className="row" style={{ marginTop: 14 }}>
            <div className="field">
              <label>Width</label>
              <input type="number" value={customW} onChange={(event) => setCustomW(Number(event.target.value))} />
            </div>
            <div className="field">
              <label>Height</label>
              <input type="number" value={customH} onChange={(event) => setCustomH(Number(event.target.value))} />
            </div>
          </div>
          <div className="row">
            <button
              type="button"
              className="btn primary"
              onClick={() => void api.applyResolution({ ...r, width: customW, height: customH }, false)}
            >
              Apply temporary
            </button>
            <button
              type="button"
              className="btn"
              onClick={async () => {
                const ok = window.confirm('Permanently write this resolution into Fortnite config? A backup is created first.')
                if (!ok) return
                await persist({ width: customW, height: customH, temporary: false })
                await api.applyResolution({ ...r, width: customW, height: customH, temporary: false }, true)
              }}
            >
              Save permanently
            </button>
            <button type="button" className="btn" onClick={() => void api.testResolution(customW, customH)}>
              Test
            </button>
            <button type="button" className="btn ghost" onClick={() => void api.backupConfig()}>
              Backup Fortnite config
            </button>
          </div>
        </section>
        <section className="card">
          <h3>How it is applied</h3>
          <div className="field">
            <label>Method</label>
            <select value={r.method} onChange={(event) => void persist({ method: event.target.value as ResolutionSettings['method'] })}>
              <option value="display">Windows display mode (recommended — actually changes the monitor)</option>
              <option value="gpu">Windows display + GPU scaling notes</option>
              <option value="automatic">Automatic (same as Windows display)</option>
              <option value="fortnite-only">GameUserSettings.ini only (does not change Windows)</option>
            </select>
          </div>
          <Toggle checked={r.applyOnLaunch} onChange={(applyOnLaunch) => void persist({ applyOnLaunch })} label="Apply with Launch Fortnite" />
          <Toggle checked={r.temporary} onChange={(temporary) => void persist({ temporary })} label="Temporary — restore when Fortnite exits" />
          <Toggle checked={r.applyGameUserSettings} onChange={(applyGameUserSettings) => void persist({ applyGameUserSettings })} label="Edit Fortnite GameUserSettings.ini" hint="Always backed up first. Never overwritten permanently without confirmation." />
          <div className="notice" style={{ marginTop: 12 }}>
            Native mode is saved before apply. Fortnite exit/crash and Restore Native put the monitor back.
            Custom sizes such as 1728×1080 or 1720×1080 are allowed. If Windows EnumDisplaySettings does not list a mode,
            Avix warns and does not force it — add the custom mode in NVIDIA/AMD/Intel first.
          </div>
        </section>
      </div>
      <section className="card" style={{ marginTop: 16 }}>
        <h3>GPU / scaling</h3>
        <p className="hint">
          Detected: {gpu?.gpu.vendor ?? '…'} · {gpu?.gpu.name ?? 'querying'}
        </p>
        <ul>
          {(gpu?.guidance ?? ['Detecting graphics adapter…']).map((line) => (
            <li key={line} className="hint">
              {line}
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
