import { AnimatePresence, motion } from 'framer-motion';
import { useEffect, useState } from 'react';
import { api } from '../api';
import { playClick } from '../audio';
import { IconPencil } from '../components/Icons';
import { Modal } from '../components/Modal';
import { motionTransition, reducedMotion } from '../format';
import { visibleName } from '../names';
import { useSession } from '../session';
import type { SettingsTab } from '../types';

const NAV: { id: SettingsTab; label: string }[] = [
  { id: 'account', label: 'Account' },
  { id: 'game', label: 'Game' },
  { id: 'launcher', label: 'Launcher' },
];

export function SettingsPage({ tab, onTab }: { tab: SettingsTab; onTab: (tab: SettingsTab) => void }) {
  return (
    <div className="page settings">
      <h1>Settings</h1>
      <div className="settings-layout">
        <nav aria-label="Settings">
          {NAV.map((item) => (
            <button key={item.id} type="button" className={tab === item.id ? 'active' : ''} onClick={() => onTab(item.id)}>
              {item.label}
            </button>
          ))}
        </nav>
        <AnimatePresence mode="wait">
          <motion.div
            key={tab}
            className={tab === 'account' ? 'settings-panel settings-panel-hug' : 'settings-panel'}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={reducedMotion() ? { duration: 0 } : motionTransition}
          >
            {tab === 'account' ? <Account /> : null}
            {tab === 'game' ? <Game /> : null}
            {tab === 'launcher' ? <Launcher /> : null}
          </motion.div>
        </AnimatePresence>
      </div>
    </div>
  );
}

function Account() {
  const { user, logout, refresh, toast } = useSession();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(user?.displayName ?? '');
  const [error, setError] = useState('');

  async function save(event: React.FormEvent) {
    event.preventDefault();
    playClick();
    setError('');
    try {
      await api('/me', { method: 'PATCH', body: { displayName: draft } });
      await refresh();
      setEditing(false);
      toast('Display name updated');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update that name.');
    }
  }

  async function onLogout() {
    playClick();
    await logout();
  }

  if (!user) return null;
  const shown = visibleName(user.discordName, user.displayName);

  return (
    <div className="account">
      <div className="account-row">
        <img src={user.avatar} alt="" className="avatar" draggable={false} />
        <div className="account-main">
          <div className="account-name">
            <strong>{shown}</strong>
            <button
              type="button"
              className="icon-btn"
              aria-label="Change display name"
              onClick={() => {
                playClick();
                setDraft(user.displayName);
                setError('');
                setEditing(true);
              }}
            >
              <IconPencil />
            </button>
          </div>
          <div className="discord-id">{user.discordId}</div>
        </div>
        <button type="button" className="sign-out" onClick={onLogout}>
          Sign Out
          <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M3 8h9M9 4.5 12.5 8 9 11.5" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
      </div>
      {editing ? (
        <Modal
          title="Change display name"
          blur
          plain
          onClose={() => {
            setEditing(false);
            setError('');
          }}
        >
          <form className="stack" onSubmit={save}>
            <p className="lede">This is how others will see you across Nexa.</p>
            <input
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              minLength={4}
              maxLength={16}
              aria-label="Display name"
              autoFocus
            />
            {error ? <p className="form-error">{error}</p> : null}
            <div className="modal-actions">
              <button
                type="button"
                className="btn ghost"
                onClick={() => {
                  playClick();
                  setEditing(false);
                  setError('');
                }}
              >
                Cancel
              </button>
              <button type="submit" className="btn primary">
                Save
              </button>
            </div>
          </form>
        </Modal>
      ) : null}
    </div>
  );
}

function Game() {
  const { settings, updateSettings, toast } = useSession();

  async function toggle(key: 'mobileBuilds' | 'resetOnRelease' | 'potatoGraphics') {
    playClick();
    try {
      await updateSettings({ [key]: !settings[key] });
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Could not save that setting.');
    }
  }

  return (
    <div className="stack">
      <div>
        <h2>Game</h2>
        <p className="lede">Stored for this profile. Nexa does not patch game files.</p>
      </div>
      <Toggle
        label="Mobile builds"
        detail="Remember that you want mobile build labels in the library."
        on={settings.mobileBuilds}
        onClick={() => toggle('mobileBuilds')}
      />
      <Toggle
        label="Reset on Release"
        detail="Remember a reset preference for the next launch. Nothing on disk is changed."
        on={settings.resetOnRelease}
        onClick={() => toggle('resetOnRelease')}
      />
      <Toggle
        label="Potato Graphics"
        detail="Remember a low-spec preference. Graphics configs stay untouched."
        on={settings.potatoGraphics}
        onClick={() => toggle('potatoGraphics')}
      />
    </div>
  );
}

function Toggle({ label, detail, on, onClick }: { label: string; detail: string; on: boolean; onClick: () => void }) {
  return (
    <button type="button" className="toggle-row" onClick={onClick} aria-pressed={on}>
      <span>
        <strong>{label}</strong>
        <small>{detail}</small>
      </span>
      <span className={on ? 'switch on' : 'switch'} />
    </button>
  );
}

function Launcher() {
  const { toast } = useSession();
  const [checking, setChecking] = useState(false);
  const [message, setMessage] = useState('');
  const [latestToast, setLatestToast] = useState(false);
  const [version, setVersion] = useState('0.1.5');

  useEffect(() => {
    api<{ version: string }>('/launcher/version')
      .then((data) => setVersion(data.version))
      .catch(() => undefined);
  }, []);

  async function check() {
    playClick();
    setChecking(true);
    setMessage('');
    setLatestToast(false);
    try {
      const status = await api<{ updateAvailable: boolean; latest: string; message: string }>('/launcher/update');
      if (!status.updateAvailable) {
        setLatestToast(true);
        return;
      }
      setMessage(`Updating to ${status.latest}…`);
      const applied = await api<{ message: string }>('/launcher/update/apply', { method: 'POST' });
      setMessage(applied.message);
    } catch (err) {
      const text = err instanceof Error ? err.message : 'Could not check for updates.';
      setMessage(text);
      toast(text);
    } finally {
      setChecking(false);
    }
  }

  return (
    <div className="stack">
      <div>
        <h2>Launcher</h2>
        <p className="version-tag">Version {version}</p>
      </div>
      <div>
        <h3>What’s new</h3>
        <ul className="notes">
          <li>Desktop shell with home, library, shop, and leaderboards.</li>
          <li>Discord sign-in and a display name cooldown.</li>
          <li>Item shop with Featured and Daily rows.</li>
        </ul>
      </div>
      <button type="button" className="btn primary" onClick={check} disabled={checking}>
        {checking ? 'Checking…' : 'Check for updates'}
      </button>
      {message ? <p className="ok-note">{message}</p> : null}
      {latestToast ? (
        <div className="import-toast" role="status">
          <span>You’re on the latest version.</span>
          <button type="button" aria-label="Dismiss" onClick={() => setLatestToast(false)}>
            ×
          </button>
        </div>
      ) : null}
    </div>
  );
}
