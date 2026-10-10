import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, clearToken, getToken, setToken } from './api';
import { applyTheme } from './themes';
import type { Settings, User } from './types';

const defaultSettings: Settings = {
  theme: 'default',
  mobileBuilds: false,
  resetOnRelease: false,
  potatoGraphics: false,
};

interface SessionValue {
  user: User | null;
  settings: Settings;
  booting: boolean;
  login: () => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  updateSettings: (patch: Partial<Settings>) => Promise<void>;
  toast: (message: string) => void;
  toastMessage: string | null;
}

const SessionContext = createContext<SessionValue | null>(null);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [settings, setSettings] = useState<Settings>(defaultSettings);
  const [booting, setBooting] = useState(true);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const toast = useCallback((message: string) => {
    setToastMessage(message);
    window.setTimeout(() => {
      setToastMessage((current) => (current === message ? null : current));
    }, 2800);
  }, []);

  const refresh = useCallback(async () => {
    const [me, nextSettings] = await Promise.all([api<User>('/me'), api<Settings>('/settings')]);
    setUser(me);
    setSettings(nextSettings);
    applyTheme(nextSettings.theme);
  }, []);

  useEffect(() => {
    const token = getToken();
    if (!token) {
      setBooting(false);
      return;
    }
    refresh()
      .catch(() => {
        clearToken();
        setUser(null);
      })
      .finally(() => setBooting(false));
  }, [refresh]);

  const login = useCallback(async () => {
    const start = await api<{ url: string; state: string }>('/auth/discord/start', { method: 'POST', body: {} });
    if (window.nexa?.openExternal) await window.nexa.openExternal(start.url);
    else {
      const opened = window.open(start.url, '_blank', 'noopener');
      if (!opened) throw new Error('Allow pop-ups so Discord can open in the browser.');
    }
    const deadline = Date.now() + 5 * 60 * 1000;
    while (Date.now() < deadline) {
      await new Promise((resolve) => window.setTimeout(resolve, 800));
      const pending = await api<{ status: string; token?: string; error?: string }>(
        `/auth/discord/pending?state=${encodeURIComponent(start.state)}`,
      );
      if (pending.status === 'error') throw new Error(pending.error || 'Discord sign-in failed.');
      if (pending.status === 'ready' && pending.token) {
        setToken(pending.token);
        await refresh();
        return;
      }
    }
    throw new Error('Discord sign-in timed out. Finish it in the browser, then try again.');
  }, [refresh]);

  const logout = useCallback(async () => {
    try {
      await api('/auth/logout', { method: 'POST', body: {} });
    } catch {
      /* local session still ends */
    }
    clearToken();
    setUser(null);
  }, []);

  const updateSettings = useCallback(
    async (patch: Partial<Settings>) => {
      const next = { ...settings, ...patch };
      setSettings(next);
      if (next.theme) applyTheme(next.theme);
      const saved = await api<Settings>('/settings', { method: 'PUT', body: next });
      setSettings(saved);
      applyTheme(saved.theme);
    },
    [settings],
  );

  const value = useMemo(
    () => ({ user, settings, booting, login, logout, refresh, updateSettings, toast, toastMessage }),
    [user, settings, booting, login, logout, refresh, updateSettings, toast, toastMessage],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession() {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error('useSession');
  return ctx;
}
