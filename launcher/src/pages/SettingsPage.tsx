import { AnimatePresence, motion } from 'framer-motion';
import { useState } from 'react';
import { api } from '../api';
import { playClick } from '../audio';
import { IconPencil } from '../components/Icons';
import { formatRemaining, motionTransition, reducedMotion } from '../format';
import { useSession } from '../session';
import type { SettingsTab } from '../types';

const PRESETS = [
  { name: 'Ion', hex: '#4c8dff' },
  { name: 'Violet', hex: '#8b5cff' },
  { name: 'Ember', hex: '#ff6a3d' },
  { name: 'Mint', hex: '#2ee6a6' },
  { name: 'Gold', hex: '#e2b657' },
  { name: 'Rose', hex: '#ff4d8d' },
];

const NAV: { id: SettingsTab; label: string }[] = [
  { id: 'account', label: 'Account' },
  { id: 'appearance', label: 'Appearance' },
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
            className="settings-panel"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={reducedMotion() ? { duration: 0 } : motionTransition}
          >
            {tab === 'account' ? <Account /> : null}
            {tab === 'appearance' ? <Appearance /> : null}
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
      toast('In-game name updated');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update that name.');
    }
  }

  async function onLogout() {
    playClick();
    await logout();
  }

  if (!user) return null;

  return (
    <div className="account">
      <div className="account-row">
        <img src={user.avatar} alt="" className="avatar" />
        <div>
          <span className="kicker">Discord</span>
          <div className="discord-name">{user.discordName}</div>
          <span className="kicker in-game-label">In-game name</span>
          {editing ? (
            <form className="name-edit" onSubmit={save}>
              <input
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                minLength={4}
                maxLength={16}
                aria-label="In-game name"
                autoFocus
              />
              <button type="submit" className="btn primary">
                Save
              </button>
              <button
                type="button"
                className="btn ghost"
                onClick={() => {
                  setEditing(false);
                  setError('');
                  setDraft(user.displayName);
                }}
              >
                Cancel
              </button>
            </form>
          ) : (
            <div className="display-row">
              <strong>{user.displayName}</strong>
              <button
                type="button"
                className="icon-btn"
                aria-label="Edit in-game name"
                onClick={() => {
                  playClick();
                  setDraft(user.displayName);
                  setEditing(true);
                }}
              >
                <IconPencil />
              </button>
            </div>
          )}
          {error ? <p className="form-error">{error}</p> : null}
          <p className="hint">
            {user.nameChange.allowed
              ? 'First change is open. After that, the in-game name waits 14 days.'
              : `You can change your name again in ${formatRemaining(user.nameChange.remainingMs)}.`}
          </p>
          <div className="discord-id">
            <span>Discord ID</span>
            <code>{user.discordId}</code>
          </div>
        </div>
      </div>
      <button type="button" className="btn ghost logout" onClick={onLogout}>
        Log out
      </button>
    </div>
  );
}

function Appearance() {
  const { settings, updateSettings, toast } = useSession();

  async function setAccent(accent: string) {
    playClick();
    try {
      await updateSettings({ accent: accent.toLowerCase() });
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Could not save that color.');
    }
  }

  return (
    <div className="stack">
      <div>
        <h2>Accent</h2>
        <p className="lede">Recolors the sidebar, buttons, focus rings, and chips.</p>
      </div>
      <div className="swatches">
        {PRESETS.map((preset) => (
          <button
            key={preset.hex}
            type="button"
            className={settings.accent.toLowerCase() === preset.hex.toLowerCase() ? 'swatch on' : 'swatch'}
            style={{ background: preset.hex }}
            aria-label={preset.name}
            onClick={() => setAccent(preset.hex)}
          />
        ))}
      </div>
      <label className="picker">
        Custom
        <input type="color" value={settings.accent.toLowerCase()} aria-label="Custom accent color" onChange={(event) => setAccent(event.target.value)} />
        <code>{settings.accent.toUpperCase()}</code>
      </label>
      <button type="button" className="btn primary sample">
        Sample button
      </button>
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
  const [checking, setChecking] = useState(false);
  const [message, setMessage] = useState('');

  async function check() {
    playClick();
    setChecking(true);
    setMessage('');
    await new Promise((resolve) => window.setTimeout(resolve, 700));
    setChecking(false);
    setMessage("You're on the latest version.");
  }

  return (
    <div className="stack">
      <div>
        <h2>Launcher</h2>
        <p className="version-tag">Version 0.1.0</p>
      </div>
      <div>
        <h3>What’s new</h3>
        <ul className="notes">
          <li>Desktop shell with home, library, shop, and leaderboards.</li>
          <li>Local admin sign-in and a display name cooldown.</li>
          <li>Item shop countdown aimed at 01:00 UK.</li>
        </ul>
      </div>
      <button type="button" className="btn primary" onClick={check} disabled={checking}>
        {checking ? 'Checking…' : 'Check for updates'}
      </button>
      {message ? <p className="ok-note">{message}</p> : null}
    </div>
  );
}
