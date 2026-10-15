import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import type { CustomEmoji, PersistedState, Settings } from './lib/storage';

type ToastItem = { id: number; text: string };

type AppContextValue = {
  settings: Settings;
  setSettings: (settings: Settings) => void;
  customEmojis: CustomEmoji[];
  addEmoji: (emoji: CustomEmoji) => void;
  removeEmoji: (id: string) => void;
  toasts: ToastItem[];
  toast: (text: string) => void;
};

const AppContext = createContext<AppContextValue | null>(null);

export function AppStateProvider({
  initial,
  onChange,
  children,
}: {
  initial: PersistedState;
  onChange: (state: PersistedState) => void;
  children: ReactNode;
}) {
  const [settings, setSettingsState] = useState(initial.settings);
  const [customEmojis, setCustomEmojis] = useState(initial.customEmojis);
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const api = useMemo<AppContextValue>(() => {
    const publish = (nextSettings: Settings, nextEmojis: CustomEmoji[]) => {
      onChange({ version: 1, settings: nextSettings, customEmojis: nextEmojis });
    };
    return {
      settings,
      customEmojis,
      toasts,
      setSettings: (next) => {
        setSettingsState(next);
        publish(next, customEmojis);
      },
      addEmoji: (emoji) => {
        const next = [emoji, ...customEmojis];
        setCustomEmojis(next);
        publish(settings, next);
      },
      removeEmoji: (id) => {
        const next = customEmojis.filter((emoji) => emoji.id !== id);
        setCustomEmojis(next);
        publish(settings, next);
      },
      toast: (text) => {
        const id = Date.now() + Math.random();
        setToasts((current) => [...current.slice(-3), { id, text }]);
        window.setTimeout(() => {
          setToasts((current) => current.filter((item) => item.id !== id));
        }, 2600);
      },
    };
  }, [settings, customEmojis, toasts, onChange]);

  return <AppContext.Provider value={api}>{children}</AppContext.Provider>;
}

export function useApp(): AppContextValue {
  const value = useContext(AppContext);
  if (!value) throw new Error('useApp must be used inside AppStateProvider');
  return value;
}
