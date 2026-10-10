export const THEMES = [
  { id: 'default', name: 'Default', color: '#07080c', edge: '#9aa3b5' },
  { id: 'void', name: 'Void', color: '#120818', edge: '#c4a6f5' },
  { id: 'ember', name: 'Ember', color: '#1a0c08', edge: '#f0a07a' },
  { id: 'frost', name: 'Frost', color: '#07141c', edge: '#8ec8e8' },
  { id: 'jade', name: 'Jade', color: '#071410', edge: '#7dcea0' },
  { id: 'bud', name: 'Bud', color: '#12160c', edge: '#c6e07a' },
  { id: 'rose', name: 'Rose', color: '#180810', edge: '#f0a0c0' },
  { id: 'sunset', name: 'Sunset', color: '#1a100c', edge: '#f0b070' },
  { id: 'aurora', name: 'Aurora', color: '#0c1218', edge: '#80d0c8' },
  { id: 'tom', name: 'Tom', color: '#10141c', edge: '#8eb4e0' },
  { id: 'noir', name: 'Noir', color: '#000000', edge: '#d7dbe4' },
  { id: 'dark', name: 'Dark', color: '#14161c', edge: '#c5cad6' },
] as const;

export function applyTheme(id: string) {
  const theme = THEMES.find((entry) => entry.id === id) || THEMES[0];
  document.documentElement.style.setProperty('--shell-bg', theme.color);
  document.documentElement.style.setProperty('--theme-edge', theme.edge);
}
