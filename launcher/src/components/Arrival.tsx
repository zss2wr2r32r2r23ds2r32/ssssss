import { useEffect, useState } from 'react';
import { api } from '../api';
import type { User } from '../types';

const WELCOME_MS = 2200;
const HOLD_MS = 480;

type QueueStatus = {
  id: string;
  spot: number;
  inLine: number;
  estimateSeconds: number;
  progress: number;
  admitted: boolean;
  headline: string;
};

function formatEstimate(seconds: number) {
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.max(1, Math.round(seconds / 60));
  return minutes === 1 ? '1 min' : `${minutes} min`;
}

export function Arrival({ user, onDone }: { user: User; onDone: () => void }) {
  const [phase, setPhase] = useState<'queue' | 'welcome'>('queue');
  const [status, setStatus] = useState<QueueStatus | null>(null);
  const name = user.discordName || user.displayName;

  useEffect(() => {
    let stopped = false;
    let timer = 0;
    let welcomeTimer = 0;
    let id = '';

    const poll = async () => {
      try {
        const next = id
          ? await api<QueueStatus>(`/queue/${id}`)
          : await api<QueueStatus>('/queue/join', { method: 'POST' });
        if (stopped) return;
        id = next.id;
        setStatus(next);
        if (next.admitted) {
          timer = window.setTimeout(() => {
            if (stopped) return;
            setPhase('welcome');
            welcomeTimer = window.setTimeout(onDone, WELCOME_MS);
          }, HOLD_MS);
          return;
        }
      } catch {
        /* keep the last real numbers and try again */
      }
      if (!stopped) timer = window.setTimeout(poll, 350);
    };

    void poll();
    return () => {
      stopped = true;
      window.clearTimeout(timer);
      window.clearTimeout(welcomeTimer);
      if (id) void api(`/queue/${id}`, { method: 'DELETE' }).catch(() => undefined);
    };
  }, [onDone]);

  const progress = status?.progress ?? 0;

  return (
    <div className="arrival">
      <section className={phase === 'queue' ? 'arrival-layer on' : 'arrival-layer'} aria-hidden={phase !== 'queue'}>
        <div className="qcard">
          <img className="qcard-logo" src="/logo.png" alt="" draggable={false} />
          <p className="qcard-status">{status?.headline ?? "You're in line"}</p>
          <div className="qcard-bar" aria-hidden="true">
            <div style={{ width: `${progress * 100}%` }} />
          </div>
          <div className="qcard-stats">
            <p>
              <strong>{status ? status.spot : '—'}</strong>
              <span>Spot</span>
            </p>
            <p>
              <strong>{status ? status.inLine : '—'}</strong>
              <span>In line</span>
            </p>
            <p>
              <strong>{status ? formatEstimate(status.estimateSeconds) : '—'}</strong>
              <span>Estimate</span>
            </p>
          </div>
          <p className="qcard-note">Players are let in a few at a time.</p>
        </div>
      </section>
      <section className={phase === 'welcome' ? 'arrival-layer on' : 'arrival-layer'} aria-hidden={phase !== 'welcome'}>
        <div className="arrival-bg" />
        <div className="arrival-shade" />
        <div className="arrival-copy">
          <img className="arrival-avatar" src={user.avatar} alt="" draggable={false} />
          <h1>Welcome, {name}</h1>
        </div>
      </section>
    </div>
  );
}
