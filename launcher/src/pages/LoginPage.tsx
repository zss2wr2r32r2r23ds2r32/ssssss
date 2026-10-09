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
        <img src="/banner-login.png" alt="" />
      </section>
    </div>
  );
}
