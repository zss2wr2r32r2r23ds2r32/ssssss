import { useState } from 'react'
import { createId } from '../../../shared/defaults'
import type { ScrimSource } from '../../../shared/types'
import { Toggle } from '../components/ui/Toggle'
import { api } from '../lib/api'
import { useApp } from '../store/AppState'

export function ScrimsPage() {
  const { config, setConfig, pushToast } = useApp()
  const [name, setName] = useState('')
  const [statusUrl, setStatusUrl] = useState('')
  if (!config) return null
  const scrims = config.scrims

  const persist = async (sources: ScrimSource[], pollSeconds = scrims.pollSeconds) => {
    const next = await api.updateScrims({ pollSeconds, sources })
    setConfig(await api.getConfig())
    return next
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>Scrim alert</h2>
          <p>
            Local notifications when you mark a scrim live, or when an optional HTTPS status URL reports live. There is no
            official Epic/org API in v1 — add your own feed or fire a test. Optional Discord webhook lives in Settings.
          </p>
        </div>
        <button
          type="button"
          className="btn"
          onClick={async () => {
            await api.refreshScrims()
            setConfig(await api.getConfig())
            pushToast({ tone: 'info', title: 'Scrims refreshed', body: 'Checked enabled status URLs.' })
          }}
        >
          Refresh now
        </button>
      </div>
      <section className="card" style={{ marginBottom: 16 }}>
        {scrims.sources.map((source) => (
          <div key={source.id} className="toggle">
            <div>
              <div>{source.name}</div>
              <div className="hint">
                {source.statusUrl ? source.statusUrl : 'Manual / test only'}
                {source.lastLiveAt ? ` · last live ${new Date(source.lastLiveAt).toLocaleString()}` : ''}
              </div>
            </div>
            <div className="row">
              <Toggle
                checked={source.enabled}
                onChange={(enabled) => void persist(scrims.sources.map((item) => (item.id === source.id ? { ...item, enabled } : item)))}
                label="Alert"
              />
              <button
                type="button"
                className="btn"
                onClick={async () => {
                  const result = await api.testScrim(source.id)
                  setConfig(await api.getConfig())
                  pushToast({ tone: result.ok ? 'success' : 'error', title: 'Scrim is on', body: result.message })
                }}
              >
                Test
              </button>
            </div>
          </div>
        ))}
        <div className="row" style={{ marginTop: 12 }}>
          <input value={name} onChange={(event) => setName(event.target.value)} placeholder="Add org / format — e.g. Trios Elite" />
          <input
            value={statusUrl}
            onChange={(event) => setStatusUrl(event.target.value)}
            placeholder="Optional https:// status URL"
          />
          <button
            type="button"
            className="btn primary"
            onClick={() => {
              const trimmed = name.trim()
              if (!trimmed) return
              const url = statusUrl.trim()
              void persist([
                ...scrims.sources,
                {
                  id: createId('scrim'),
                  name: trimmed,
                  enabled: true,
                  statusUrl: url.startsWith('https://') ? url : null,
                  lastLiveAt: null
                }
              ])
              setName('')
              setStatusUrl('')
            }}
          >
            Add
          </button>
        </div>
      </section>
      <section className="card">
        <h3>How alerts work</h3>
        <p className="hint">
          Enable a row, then click Test to toast “Scrim is on — {`{name}`}` and send the same line to your webhook if one is
          saved. If you paste an https status URL that returns {`{ "live": true }`} or the word “live”, Refresh/polling can
          fire it automatically. Avix never scrapes Discord without your webhook and never touches Fortnite memory.
        </p>
        <div className="field" style={{ marginTop: 12 }}>
          <label>Poll interval (seconds)</label>
          <input
            type="number"
            min={20}
            max={600}
            value={scrims.pollSeconds}
            onChange={(event) => void persist(scrims.sources, Number(event.target.value))}
          />
        </div>
      </section>
    </div>
  )
}
