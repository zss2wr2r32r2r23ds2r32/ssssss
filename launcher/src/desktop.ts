const DISCORD_AUTH = 'https://discord.com/oauth2/authorize?';

export async function installDesktop() {
  const host = window as Window & { __TAURI_INTERNALS__?: unknown };
  if (!host.__TAURI_INTERNALS__) return;
  const { getCurrentWindow } = await import('@tauri-apps/api/window');
  const { invoke } = await import('@tauri-apps/api/core');
  const win = getCurrentWindow();
  window.nexa = {
    minimize: () => {
      void win.minimize();
    },
    maximize: () => {
      void win.toggleMaximize();
    },
    close: () => {
      void win.close();
    },
    apiBase: 'http://127.0.0.1:4177',
    openExternal: async (url: string) => {
      if (!url.startsWith(DISCORD_AUTH)) throw new Error('Refusing to open that address.');
      await invoke('open_external', { url });
    },
    pickFolder: async () => {
      const picked = await invoke<string | null>('pick_folder');
      return picked || '';
    },
  };
}
