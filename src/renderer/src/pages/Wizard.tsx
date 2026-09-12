import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useState } from 'react'
import { CROSSHAIR_PRESETS, RESOLUTION_PRESETS } from '../../../shared/types'
import { CrosshairMark } from '../components/crosshair/CrosshairMark'
import { api } from '../lib/api'
import { useApp } from '../store/AppState'

const STEPS = ['Welcome', 'Detect', 'Install', 'Monitor', 'Resolution', 'Crosshair', 'Finish'] as const

export function Wizard() {
  const { config, profile, setConfig } = useApp()
  const [step, setStep] = useState(0)
  const [detecting, setDetecting] = useState(false)
  const [detectNote, setDetectNote] = useState<string | null>(null)

  useEffect(() => {
    if (step !== 1 || !config || config.fortnitePath) return
    let cancelled = false
    setDetecting(true)
    void api
      .detectFortnite()
      .then(async (info) => {
        if (cancelled) return
        setConfig(await api.getConfig())
        setDetectNote(info.valid ? `Found ${info.path}` : info.reason ?? 'Not found. You can browse on the next step.')
        setDetecting(false)
      })
      .catch(() => {
        if (cancelled) return
        setDetectNote('Could not search for Fortnite.')
        setDetecting(false)
      })
    return () => {
      cancelled = true
    }
  }, [step, config?.fortnitePath, setConfig])

  if (!config || !profile) return null

  const next = () => setStep((s) => Math.min(STEPS.length - 1, s + 1))
  const back = () => setStep((s) => Math.max(0, s - 1))

  return (
    <div className="wizard">
      <div className="wizard-card">
        <div className="steps">
          {STEPS.map((label, index) => (
            <i key={label} className={index <= step ? 'on' : ''} title={label} />
          ))}
        </div>
        <AnimatePresence mode="wait">
          <motion.div key={step} initial={{ opacity: 0, x: 16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -16 }}>
            {step === 0 && (
              <>
                <p className="hint">Welcome to Avix</p>
                <h2 className="hero-word" style={{ fontSize: 72 }}>Avix</h2>
                <p className="hero-sub">Your Fortnite competitive setup, all in one place.</p>
                <p className="hint">
                  Path detection, profiles, a click-through crosshair overlay, temporary resolution handling, live
                  resource meters, and a rules-aware key repeater — without touching anti-cheat or Fortnite memory.
                </p>
              </>
            )}
            {step === 1 && (
              <>
                <h2>Detect Fortnite</h2>
                <p className="hint">Avix searches common Epic locations and launcher manifests. Nothing is hardcoded as the only path.</p>
                <div className="card" style={{ margin: '16px 0' }}>
                  <strong>{config.fortnitePath ? 'Install candidate ready' : 'Not found yet'}</strong>
                  <div className="hint">{config.fortnitePath ?? 'You can browse on the next step if auto-detect misses.'}</div>
                  {detectNote ? <div className="hint" style={{ marginTop: 8 }}>{detectNote}</div> : null}
                </div>
                <button
                  type="button"
                  className="btn primary"
                  disabled={detecting}
                  onClick={async () => {
                    setDetecting(true)
                    const info = await api.detectFortnite()
                    setConfig(await api.getConfig())
                    setDetectNote(info.valid ? `Found ${info.path}` : info.reason ?? 'Not found. You can browse on the next step.')
                    setDetecting(false)
                  }}
                >
                  {detecting ? 'Searching…' : 'Search now'}
                </button>
              </>
            )}
            {step === 2 && (
              <>
                <h2>Install path</h2>
                <p className="hint">
                  Accepts FortniteClient-Win64-Shipping.exe, FortniteBootstrapper.exe, or Fortnite.exe. Missing installs are
                  fine — the app still opens.
                </p>
                <div className="hint" style={{ margin: '12px 0' }}>{config.fortnitePath ?? 'No path stored'}</div>
                {detectNote ? <div className="hint">{detectNote}</div> : null}
                <div className="row">
                  <button type="button" className="btn" onClick={async () => {
                    const info = await api.browseFortnite()
                    setConfig(await api.getConfig())
                    if (info.valid) {
                      setDetectNote(`Using ${info.path}`)
                    } else if (info.reason && info.reason !== 'Browse cancelled.') {
                      setDetectNote(info.reason)
                    }
                  }}>
                    Browse for Fortnite executable
                  </button>
                </div>
              </>
            )}
            {step === 3 && (
              <>
                <h2>Session monitor</h2>
                <p className="hint">
                  While Fortnite is running, Home can show CPU, GPU, RAM, and temperatures from documented OS APIs only.
                  Nothing is injected into the game.
                </p>
              </>
            )}
            {step === 4 && (
              <>
                <h2>Resolution preference</h2>
                <p className="hint">Pick an example. This is stored on your profile and applied only when you ask — preferably Fortnite-only and temporary.</p>
                <div className="row" style={{ marginTop: 12 }}>
                  {RESOLUTION_PRESETS.slice(0, 4).map((preset) => (
                    <button
                      key={preset.id}
                      type="button"
                      className={`btn ${profile.resolution.width === preset.width ? 'primary' : ''}`}
                      onClick={() => void api.updateProfile(profile.id, { resolution: { ...profile.resolution, width: preset.width, height: preset.height } }).then(setConfig)}
                    >
                      {preset.label}
                    </button>
                  ))}
                </div>
              </>
            )}
            {step === 5 && (
              <>
                <h2>Crosshair</h2>
                <p className="hint">Choose a starting mark. You can refine color, gap, and custom images later.</p>
                <div className="preset-grid" style={{ marginTop: 16 }}>
                  {CROSSHAIR_PRESETS.map((preset) => (
                    <button
                      key={preset.id}
                      type="button"
                      className={`preset ${profile.crosshair.presetId === preset.id ? 'active' : ''}`}
                      onClick={() => void api.updateCrosshair({ ...profile.crosshair, ...preset.settings, presetId: preset.id }).then(setConfig)}
                    >
                      <CrosshairMark settings={{ ...profile.crosshair, ...preset.settings }} size={40} />
                    </button>
                  ))}
                </div>
              </>
            )}
            {step === 6 && (
              <>
                <h2>You are set</h2>
                <p className="hero-sub">Home is ready whenever you are.</p>
                <p className="hint">Profiles Competitive, Aggressive, and Native are already seeded. Launch never permanently changes Windows unless you explicitly save.</p>
              </>
            )}
          </motion.div>
        </AnimatePresence>
        <div className="row" style={{ marginTop: 28, justifyContent: 'space-between' }}>
          <button type="button" className="btn ghost" onClick={back} disabled={step === 0}>
            Back
          </button>
          {step < STEPS.length - 1 ? (
            <button type="button" className="btn primary" onClick={next}>
              Continue
            </button>
          ) : (
            <button type="button" className="btn primary" onClick={() => void api.completeWizard().then(setConfig)}>
              Finish → Home
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
