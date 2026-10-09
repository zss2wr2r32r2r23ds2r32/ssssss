import { useState } from 'react';
import { playClick } from '../audio';
import { IconDiscord } from '../components/Icons';
import { useSession } from '../session';

export function LoginPage() {
  const { login, toast } = useSession();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function onContinue() {
    if (busy) return;
    playClick();
    setBusy(true);
    setError('');
    try {
      await login();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Sign-in failed.';
      setError(message);
      toast(message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login">
      <section className="login-left">
        <img src="/logo.png" alt="Nexa" className="login-logo" />
        <div className="login-copy">
          <h1>Sign In with Discord</h1>
          <p>Link your Discord account to Nexa and pick up where you left off.</p>
          <button type="button" className="discord-btn" onClick={onContinue} disabled={busy}>
            <IconDiscord />
            Continue with Discord
          </button>
          {error ? <p className="form-error">{error}</p> : null}
        </div>
      </section>
      <section className="login-art" aria-hidden="true">
        <svg className="login-art-svg" viewBox="0 0 900 1100" preserveAspectRatio="xMidYMid slice">
          <defs>
            <linearGradient id="sky" x1="0" y1="0" x2="0.2" y2="1">
              <stop offset="0" stopColor="#12151c" />
              <stop offset="0.45" stopColor="#07080c" />
              <stop offset="1" stopColor="#030406" />
            </linearGradient>
            <radialGradient id="glow" cx="70%" cy="28%" r="45%">
              <stop offset="0" stopColor="#d7e7f4" stopOpacity="0.28" />
              <stop offset="1" stopColor="#d7e7f4" stopOpacity="0" />
            </radialGradient>
            <linearGradient id="arch" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="#f7fbff" />
              <stop offset="1" stopColor="#7f9bb3" />
            </linearGradient>
          </defs>
          <rect width="900" height="1100" fill="url(#sky)" />
          <rect width="900" height="1100" fill="url(#glow)" />
          {Array.from({ length: 40 }).map((_, i) => (
            <circle
              key={i}
              cx={(i * 97) % 880 + 10}
              cy={(i * 53) % 520 + 20}
              r={(i % 3) + 0.6}
              fill="#e7f1f8"
              opacity={0.25 + (i % 5) * 0.08}
            />
          ))}
          <circle cx="640" cy="230" r="150" fill="none" stroke="#e7f2fb" strokeWidth="2" opacity="0.55" />
          <circle cx="640" cy="230" r="112" fill="none" stroke="#e7f2fb" strokeWidth="1" opacity="0.28" />
          <path d="M500 230 a140 140 0 0 1 80 250" stroke="#f4fbff" strokeWidth="3" fill="none" opacity="0.7" />
          <path d="M40 760 L180 560 L300 690 L430 470 L560 700 L720 520 L900 760 Z" fill="#10131a" />
          <path d="M80 820 L240 640 L390 760 L520 560 L690 760 L860 620 L940 860 L0 860 Z" fill="#0a0c11" />
          <path d="M330 860 V470" stroke="url(#arch)" strokeWidth="8" />
          <path d="M570 860 V470" stroke="url(#arch)" strokeWidth="8" />
          <path d="M330 490 Q450 360 570 490" stroke="url(#arch)" strokeWidth="8" fill="none" />
          <path d="M360 860 V560 Q450 500 540 560 V860" fill="#121722" stroke="#d5e4f0" strokeWidth="2" />
          <path d="M430 700 h40 v70 h-40z" fill="#07080c" stroke="#9eb4c6" />
          <circle cx="450" cy="640" r="10" fill="#e7f3fb" />
          <path d="M120 300 l26 46 h-52z" fill="none" stroke="#d5e6f2" strokeWidth="2" />
          <path d="M760 420 l18 32 h-36z" fill="none" stroke="#d5e6f2" strokeWidth="2" opacity="0.7" />
          <path d="M210 180 l14 24 h-28z" fill="#e7f2fb" opacity="0.8" />
          <path d="M0 900 H900" stroke="#d7e6f2" strokeOpacity="0.15" />
          <ellipse cx="450" cy="980" rx="220" ry="28" fill="#d7e6f2" opacity="0.05" />
        </svg>
      </section>
    </div>
  );
}
