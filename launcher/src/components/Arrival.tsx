import { useEffect, useState } from 'react';
import type { User } from '../types';

const QUEUE_MS = 3400;
const WELCOME_MS = 2200;

export function Arrival({ user, onDone }: { user: User; onDone: () => void }) {
  const [phase, setPhase] = useState<'queue' | 'welcome'>('queue');
  const [progress, setProgress] = useState(0);
  const name = user.discordName || user.displayName;

  useEffect(() => {
    const started = performance.now();
    let frame = 0;
    let welcomeTimer = 0;
    const tick = (now: number) => {
      const next = Math.min(1, (now - started) / QUEUE_MS);
      setProgress(next);
      if (next < 1) {
        frame = window.requestAnimationFrame(tick);
        return;
      }
      setPhase('welcome');
      welcomeTimer = window.setTimeout(onDone, WELCOME_MS);
    };
    frame = window.requestAnimationFrame(tick);
    return () => {
      window.cancelAnimationFrame(frame);
      window.clearTimeout(welcomeTimer);
    };
  }, [onDone]);

  return (
    <div className="arrival">
      <section className={phase === 'queue' ? 'arrival-layer on' : 'arrival-layer'} aria-hidden={phase !== 'queue'}>
        <div className="spot-card">
          <p>Finding you a spot</p>
          <div className="spot-bar" aria-hidden="true">
            <div style={{ width: `${progress * 100}%` }} />
          </div>
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
