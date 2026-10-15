import { normalizeHex } from './hex';

export type Settings = {
  accent: string;
  secondary: string;
  apiEnabled: boolean;
  apiBaseUrl: string;
  apiKey: string;
  apiModel: string;
};

export type CustomEmoji = {
  id: string;
  char: string;
  name: string;
  category: string;
};

export type PersistedState = {
  version: 1;
  customEmojis: CustomEmoji[];
  settings: Settings;
};

export const DEFAULT_SETTINGS: Settings = {
  accent: '#ff0000',
  secondary: '#94ff00',
  apiEnabled: false,
  apiBaseUrl: 'https://api.openai.com/v1',
  apiKey: '',
  apiModel: 'gpt-4o-mini',
};

const STORAGE_KEY = 'avix-studios';

export function defaultState(): PersistedState {
  return { version: 1, customEmojis: [], settings: { ...DEFAULT_SETTINGS } };
}

export function normalizeState(raw: unknown): PersistedState {
  const base = defaultState();
  if (!raw || typeof raw !== 'object') return base;
  const record = raw as Partial<PersistedState>;
  const settings: Partial<Settings> = record.settings ?? {};
  const custom = Array.isArray(record.customEmojis) ? record.customEmojis : [];
  return {
    version: 1,
    customEmojis: custom
      .filter((entry): entry is CustomEmoji => {
        if (!entry || typeof entry !== 'object') return false;
        const item = entry as Partial<CustomEmoji>;
        return Boolean(item.id && item.char && item.name && item.category);
      })
      .slice(0, 400)
      .map((entry) => ({
        id: String(entry.id),
        char: Array.from(String(entry.char)).slice(0, 16).join(''),
        name: String(entry.name).slice(0, 48),
        category: String(entry.category).slice(0, 32),
      })),
    settings: {
      accent: normalizeHex(String(settings.accent ?? DEFAULT_SETTINGS.accent)),
      secondary: normalizeHex(String(settings.secondary ?? DEFAULT_SETTINGS.secondary), '#94ff00'),
      apiEnabled: Boolean(settings.apiEnabled),
      apiBaseUrl: String(settings.apiBaseUrl ?? DEFAULT_SETTINGS.apiBaseUrl).slice(0, 300),
      apiKey: String(settings.apiKey ?? ''),
      apiModel: String(settings.apiModel ?? DEFAULT_SETTINGS.apiModel).slice(0, 80),
    },
  };
}

export async function loadState(): Promise<PersistedState> {
  try {
    if (window.avix?.getStore) return normalizeState(await window.avix.getStore());
    const raw = localStorage.getItem(STORAGE_KEY);
    return normalizeState(raw ? JSON.parse(raw) : {});
  } catch {
    return defaultState();
  }
}

export async function saveState(state: PersistedState): Promise<void> {
  const payload = normalizeState(state);
  if (window.avix?.setStore) {
    await window.avix.setStore(payload);
    return;
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
}
