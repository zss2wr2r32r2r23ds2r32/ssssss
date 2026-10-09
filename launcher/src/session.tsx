import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, clearToken, getToken, setToken } from './api';
import { applyAccent } from './accent';
import type { Settings, User } from './types';

const defaultSettings: Settings = {
  accent: '#4c8dff',
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
    applyAccent(nextSettings.accent);
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
    const result = await api<{ token: string; user: User }>('/auth/dev-login', { method: 'POST', body: {} });
    setToken(result.token);
    await refresh();
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
      if (next.accent) applyAccent(next.accent);
      const saved = await api<Settings>('/settings', { method: 'PUT', body: next });
      setSettings(saved);
      applyAccent(saved.accent);
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
