import { useEffect, useState } from 'react'
import type { MonitorSnapshot } from '../../../shared/types'
import { api } from '../lib/api'

function Meter({ value }: { value: number | null }) {
  return (
    <div className="meter">
      <i style={{ width: `${Math.max(0, Math.min(100, value ?? 0))}%` }} />
    </div>
  )
}

export function PerformancePage() {
  const [snap, setSnap] = useState<MonitorSnapshot | null>(null)

  useEffect(() => {
    let alive = true
    const tick = async () => {
      try {
        const next = await api.monitor()
        if (alive) setSnap(next)
      } catch {
        // Keep last snapshot.
      }
    }
    void tick()
    const id = setInterval(() => void tick(), 1500)
    return () => {
      alive = false
      clearInterval(id)
    }
  }, [])

  const ramPct =
    snap?.ramUsedMb && snap.ramTotalMb ? Math.round((snap.ramUsedMb / snap.ramTotalMb) * 100) : null

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>Performance</h2>
          <p>Live system resource usage from Windows/OS counters. No Fortnite injection, no “FPS magic,” no closing other apps.</p>
        </div>
      </div>
      <div className="grid grid-3">
        <section className="card stat">
          <span>CPU</span>
          <strong>{snap?.cpuLoad ?? '—'}%</strong>
          <Meter value={snap?.cpuLoad ?? null} />
          <div className="hint">{snap?.cpuTemp != null ? `${snap.cpuTemp}°C` : 'Temperature unavailable'}</div>
        </section>
        <section className="card stat">
          <span>RAM</span>
          <strong>{ramPct != null ? `${ramPct}%` : '—'}</strong>
          <Meter value={ramPct} />
          <div className="hint">
            {snap?.ramUsedMb && snap.ramTotalMb ? `${snap.ramUsedMb} / ${snap.ramTotalMb} MB` : 'Reading memory…'}
          </div>
        </section>
        <section className="card stat">
          <span>GPU</span>
          <strong>{snap?.gpuLoad != null ? `${snap.gpuLoad}%` : 'n/a'}</strong>
          <Meter value={snap?.gpuLoad ?? null} />
          <div className="hint">{snap?.gpuName ?? 'GPU counters are Windows-only when the driver exposes them.'}</div>
        </section>
      </div>
      <section className="card" style={{ marginTop: 16 }}>
        <h3>Avix process</h3>
        <p className="hint">This launcher’s own footprint — useful to see we are not a heavy background load.</p>
        <div className="grid grid-2" style={{ marginTop: 12 }}>
          <div className="stat">
            <span>Avix CPU</span>
            <strong>{snap?.appCpu != null ? `${snap.appCpu}%` : '—'}</strong>
          </div>
          <div className="stat">
            <span>Avix RAM</span>
            <strong>{snap?.appRssMb != null ? `${snap.appRssMb} MB` : '—'}</strong>
          </div>
        </div>
      </section>
    </div>
  )
}
