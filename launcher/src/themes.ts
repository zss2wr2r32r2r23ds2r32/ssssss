export const THEMES = [
  { id: 'default', name: 'Default', gradient: 'linear-gradient(165deg, #07080c 0%, #151922 100%)' },
  { id: 'void', name: 'Void', gradient: 'linear-gradient(160deg, #07010f 0%, #2a0d4a 48%, #100616 100%)' },
  { id: 'ember', name: 'Ember', gradient: 'linear-gradient(155deg, #140804 0%, #8a2e12 46%, #2a0c08 100%)' },
  { id: 'frost', name: 'Frost', gradient: 'linear-gradient(160deg, #071018 0%, #1c4d66 50%, #0b1c28 100%)' },
  { id: 'jade', name: 'Jade', gradient: 'linear-gradient(160deg, #04110c 0%, #0f6b45 48%, #062018 100%)' },
  { id: 'bud', name: 'Bud', gradient: 'linear-gradient(160deg, #10160a 0%, #6d8f2e 46%, #1a220e 100%)' },
  { id: 'rose', name: 'Rose', gradient: 'linear-gradient(155deg, #16060e 0%, #a12358 48%, #2a0c16 100%)' },
  { id: 'sunset', name: 'Sunset', gradient: 'linear-gradient(145deg, #1a0c18 0%, #c4552a 42%, #6a2a78 100%)' },
  { id: 'aurora', name: 'Aurora', gradient: 'linear-gradient(140deg, #04140f 0%, #1d8a7a 38%, #3a3d9a 72%, #120818 100%)' },
  { id: 'tom', name: 'Tom', gradient: 'linear-gradient(160deg, #0c1018 0%, #24507a 50%, #101820 100%)' },
  { id: 'noir', name: 'Noir', gradient: 'linear-gradient(180deg, #000000 0%, #0a0a0a 100%)' },
  { id: 'dark', name: 'Dark', gradient: 'linear-gradient(180deg, #0e0e12 0%, #1a1c22 100%)' },
] as const;

export function applyTheme(id: string) {
  const theme = THEMES.find((entry) => entry.id === id) || THEMES[0];
  document.documentElement.style.setProperty('--shell-bg', theme.gradient);
}
