import { ALLOWED_MACRO_KEYS, MACRO_INTERVAL_PRESETS, type MacroSettings } from '../../../shared/types'
import { Toggle } from '../components/ui/Toggle'
import { api } from '../lib/api'
import { useApp } from '../store/AppState'

export function MacroPage() {
  const { profile, setConfig, pushToast } = useApp()
  if (!profile) return null
  const m = profile.macro

  const persist = async (patch: Partial<MacroSettings>) => {
    setConfig(await api.updateMacro({ ...m, ...patch }))
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>Macro</h2>
          <p>Simple interval key/mouse repeat. No memory, packets, injection, aim, recoil, ESP, or anti-cheat interaction.</p>
        </div>
      </div>
      <div className="notice danger">
        <strong>Epic Games rules risk.</strong> Using macros in Fortnite can violate Epic’s rules and may result in penalties
        including account action. You are responsible for compliance. Nautical does not bypass Easy Anti-Cheat, does not
        read Fortnite memory, and does not automate aiming. Leave this page disabled if you play in environments that
        forbid macros.
      </div>
      <div className="grid grid-2" style={{ marginTop: 16 }}>
        <section className="card">
          <Toggle
            checked={m.acknowledgedRisk}
            onChange={(acknowledgedRisk) => void persist({ acknowledgedRisk, enabled: acknowledgedRisk ? m.enabled : false })}
            label="I understand the rules risk"
            hint="Required before the macro can be armed."
          />
          <Toggle checked={m.enabled} onChange={(enabled) => void persist({ enabled })} label="Enable macro" hint="Starts with Fortnite only when this is on and the notice is accepted." />
          <div className="field">
            <label>Repeat key</label>
            <select value={m.key} onChange={(event) => void persist({ key: event.target.value })}>
              {ALLOWED_MACRO_KEYS.map((key) => (
                <option key={key} value={key}>
                  {key}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label>Mouse (optional)</label>
            <select value={m.mouseButton} onChange={(event) => void persist({ mouseButton: event.target.value as MacroSettings['mouseButton'] })}>
              <option value="none">None</option>
              <option value="left">Left click</option>
              <option value="right">Right click</option>
              <option value="middle">Middle click</option>
            </select>
          </div>
          <div className="field">
            <label>Interval {m.intervalSec.toFixed(2)}s</label>
            <input
              type="range"
              min={0.1}
              max={2}
              step={0.01}
              value={m.intervalSec}
              onChange={(event) => void persist({ intervalSec: Number(event.target.value) })}
            />
            <div className="row">
              {MACRO_INTERVAL_PRESETS.map((value) => (
                <button key={value} type="button" className="btn ghost" onClick={() => void persist({ intervalSec: value })}>
                  {value.toFixed(2)}s
                </button>
              ))}
            </div>
          </div>
        </section>
        <section className="card">
          <div className="field">
            <label>Run mode</label>
            <select value={m.mode} onChange={(event) => void persist({ mode: event.target.value as MacroSettings['mode'] })}>
              <option value="fortnite-focus">Only while Fortnite is focused (default)</option>
              <option value="hold">Hold-to-run (activation key)</option>
              <option value="toggle">Toggle-to-run (activation key)</option>
            </select>
          </div>
          <div className="field">
            <label>Activation key</label>
            <select value={m.activationKey} onChange={(event) => void persist({ activationKey: event.target.value })}>
              {ALLOWED_MACRO_KEYS.map((key) => (
                <option key={key} value={key}>
                  {key}
                </option>
              ))}
            </select>
          </div>
          <Toggle
            checked={m.onlyWhileFortniteFocused}
            onChange={(onlyWhileFortniteFocused) => void persist({ onlyWhileFortniteFocused })}
            label="Focus gate"
            hint="Even in hold/toggle modes, repeats stay gated to a focused Fortnite window by default."
          />
          <div className="row" style={{ marginTop: 12 }}>
            <button
              type="button"
              className="btn primary"
              onClick={async () => {
                const result = await api.startMacro()
                pushToast({ tone: result.ok ? 'success' : 'error', title: 'Macro', body: result.message })
              }}
            >
              Start
            </button>
            <button type="button" className="btn" onClick={() => void api.stopMacro()}>
              Stop
            </button>
          </div>
        </section>
      </div>
    </div>
  )
}
