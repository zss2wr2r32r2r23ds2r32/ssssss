import { useState } from 'react'
import { CROSSHAIR_PRESETS, type CrosshairSettings } from '../../../shared/types'
import { CrosshairMark } from '../components/crosshair/CrosshairMark'
import { Toggle } from '../components/ui/Toggle'
import { api } from '../lib/api'
import { useApp } from '../store/AppState'

export function CrosshairPage() {
  const { profile, config, setConfig, pushToast } = useApp()
  const [presetName, setPresetName] = useState('My preset')
  if (!profile || !config) return null
  const x = profile.crosshair

  const update = async (patch: Partial<CrosshairSettings>) => {
    setConfig(await api.updateCrosshair({ ...x, ...patch }))
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>Crosshair</h2>
          <p>Always-on-top, click-through overlay. Prefer exclude-from-capture when Windows allows it.</p>
        </div>
      </div>
      <div className="grid grid-2">
        <section className="card">
          <div className="preview-stage">
            <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center' }}>
              <CrosshairMark settings={x} size={180} />
            </div>
          </div>
          <div className="row" style={{ marginTop: 12 }}>
            <button type="button" className="btn" onClick={() => void api.startOverlay()}>
              Preview overlay
            </button>
            <button type="button" className="btn ghost" onClick={() => void api.stopOverlay()}>
              Stop overlay
            </button>
            <button
              type="button"
              className="btn"
              onClick={async () => {
                const result = await api.importCrosshairImage()
                if (result.data) setConfig(result.data)
                pushToast({ tone: result.ok ? 'success' : 'warn', title: 'Import', body: result.message })
              }}
            >
              Import PNG/SVG
            </button>
          </div>
        </section>
        <section className="card">
          <Toggle checked={x.enabled} onChange={(enabled) => void update({ enabled })} label="Enable overlay" hint="Starts with Fortnite when the launch sequence runs." />
          <Toggle
            checked={x.onlyWhileFortnite}
            onChange={(onlyWhileFortnite) => void update({ onlyWhileFortnite })}
            label="Only while Fortnite is running"
            hint="Default on. Overlay stops when the session ends."
          />
          <h3>Presets</h3>
          <div className="preset-grid">
            {CROSSHAIR_PRESETS.map((preset) => (
              <button
                key={preset.id}
                type="button"
                className={`preset ${x.presetId === preset.id ? 'active' : ''}`}
                onClick={() => void update({ ...preset.settings, presetId: preset.id })}
              >
                <CrosshairMark settings={{ ...x, ...preset.settings, presetId: preset.id }} size={44} />
              </button>
            ))}
          </div>
          <p className="hint">
            Classic Dot, Small Cross, Hollow Cross, T Cross, Plus, Precision Dot, Four Lines, Circle, Dot + Circle, Minimal.
          </p>
        </section>
      </div>
      <div className="grid grid-2" style={{ marginTop: 16 }}>
        <section className="card">
          <h3>Custom</h3>
          <div className="field">
            <label>Color</label>
            <input type="color" value={x.color} onChange={(event) => void update({ color: event.target.value })} />
          </div>
          <div className="field">
            <label>Opacity {Math.round(x.opacity * 100)}%</label>
            <input type="range" min={0.15} max={1} step={0.01} value={x.opacity} onChange={(event) => void update({ opacity: Number(event.target.value) })} />
          </div>
          <div className="field">
            <label>Size {x.size}</label>
            <input type="range" min={1} max={40} value={x.size} onChange={(event) => void update({ size: Number(event.target.value) })} />
          </div>
          <div className="field">
            <label>Thickness {x.thickness}</label>
            <input type="range" min={0} max={10} value={x.thickness} onChange={(event) => void update({ thickness: Number(event.target.value) })} />
          </div>
          <div className="field">
            <label>Gap {x.gap}</label>
            <input type="range" min={0} max={24} value={x.gap} onChange={(event) => void update({ gap: Number(event.target.value) })} />
          </div>
          <div className="field">
            <label>Rotation {x.rotation}°</label>
            <input type="range" min={0} max={180} value={x.rotation} onChange={(event) => void update({ rotation: Number(event.target.value) })} />
          </div>
        </section>
        <section className="card">
          <Toggle checked={x.outline} onChange={(outline) => void update({ outline })} label="Outline" />
          <div className="field">
            <label>Outline thickness {x.outlineThickness}</label>
            <input type="range" min={0} max={6} value={x.outlineThickness} onChange={(event) => void update({ outlineThickness: Number(event.target.value) })} />
          </div>
          <Toggle checked={x.showDot} onChange={(showDot) => void update({ showDot })} label="Center dot" />
          <div className="field">
            <label>Dot size {x.dotSize}</label>
            <input type="range" min={1} max={12} value={x.dotSize} onChange={(event) => void update({ dotSize: Number(event.target.value) })} />
          </div>
          <Toggle checked={x.horizontal} onChange={(horizontal) => void update({ horizontal })} label="Horizontal lines" />
          <Toggle checked={x.vertical} onChange={(vertical) => void update({ vertical })} label="Vertical lines" />
          <Toggle checked={x.centerGap} onChange={(centerGap) => void update({ centerGap })} label="Center gap" />
          <div className="field">
            <label>Save preset</label>
            <div className="row">
              <input value={presetName} onChange={(event) => setPresetName(event.target.value)} />
              <button
                type="button"
                className="btn"
                onClick={async () => {
                  setConfig(await api.saveCrosshairPreset(presetName || 'Custom'))
                  pushToast({ tone: 'success', title: 'Preset saved' })
                }}
              >
                Save
              </button>
            </div>
          </div>
          {config.savedCrosshairPresets.length ? (
            <div className="row">
              {config.savedCrosshairPresets.map((preset) => (
                <button
                  key={preset.id}
                  type="button"
                  className="btn"
                  onClick={() => void update({ ...preset.settings, presetId: preset.id })}
                  onContextMenu={(event) => {
                    event.preventDefault()
                    void api.deleteCrosshairPreset(preset.id).then(setConfig)
                  }}
                >
                  {preset.name}
                </button>
              ))}
            </div>
          ) : (
            <div className="empty">No saved presets yet.</div>
          )}
        </section>
      </div>
    </div>
  )
}
