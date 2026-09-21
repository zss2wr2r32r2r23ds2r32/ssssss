const { app, BrowserWindow, Menu, ipcMain, clipboard } = require('electron');
const fs = require('fs');
const path = require('path');

const isDev = !app.isPackaged;
const DEV_URL = 'http://127.0.0.1:5173';

function storeFile() {
  return path.join(app.getPath('userData'), 'avix-store.json');
}

function readStore() {
  try {
    const raw = fs.readFileSync(storeFile(), 'utf8');
    const data = JSON.parse(raw);
    if (!data || typeof data !== 'object' || Array.isArray(data)) return {};
    return data;
  } catch {
    return {};
  }
}

function writeStore(data) {
  const file = storeFile();
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const safe = JSON.parse(JSON.stringify(data));
  fs.writeFileSync(file, JSON.stringify(safe, null, 2), 'utf8');
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1180,
    height: 760,
    minWidth: 960,
    minHeight: 640,
    title: 'Avix Studios',
    backgroundColor: '#0c0c0e',
    autoHideMenuBar: true,
    show: false,
    icon: path.join(__dirname, '../build/icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  win.setMenuBarVisibility(false);
  win.once('ready-to-show', () => win.show());

  if (isDev) {
    win.loadURL(DEV_URL);
    if (process.env.AVIX_DEVTOOLS === '1') {
      win.webContents.openDevTools({ mode: 'detach' });
    }
  } else {
    win.loadFile(path.join(__dirname, '../dist/index.html'));
  }

  return win;
}

ipcMain.handle('store:get', () => readStore());

ipcMain.handle('store:set', (_event, data) => {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new Error('Invalid store payload');
  }
  const encoded = JSON.stringify(data);
  if (encoded.length > 1_500_000) {
    throw new Error('Store is too large');
  }
  writeStore(data);
  return true;
});

ipcMain.handle('clipboard:write', (_event, text) => {
  clipboard.writeText(String(text ?? ''));
  return true;
});

ipcMain.handle('ai:complete', async (_event, payload) => {
  const baseUrl = String(payload?.baseUrl ?? '').trim();
  const apiKey = String(payload?.apiKey ?? '').trim();
  const model = String(payload?.model ?? '').trim();
  const instruction = String(payload?.instruction ?? '');
  const yamlText = String(payload?.yaml ?? '');
  const accent = String(payload?.accent ?? '#ff0000');
  const secondary = String(payload?.secondary ?? '#94ff00');

  if (!apiKey) throw new Error('No API key set');
  if (!model) throw new Error('No model set');
  if (yamlText.length > 120_000) throw new Error('Config is too large');

  let url;
  try {
    url = new URL(baseUrl);
  } catch {
    throw new Error('Invalid API base URL');
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error('API base URL must be http or https');
  }
  const endpoint = new URL('chat/completions', url.href.endsWith('/') ? url.href : `${url.href}/`);

  const system = [
    'You are Avix Studios, a Minecraft Java config stylist.',
    'Rewrite the user YAML/plugin config to satisfy the instruction.',
    'Return only YAML. No markdown fences and no commentary.',
    'When generating item or head lore in bounty style, follow this pattern:',
    "head:",
    "  name: '&#ff0000%player%'",
    '  lore:',
    "  - '&7[ʙᴏᴜɴᴛʏ]'",
    "  - ''",
    "  - '&#ff0000Description:'",
    "  - '&#ff0000| &fKill &#ff0000%player% To'",
    "  - '&#ff0000| &fGain Money'",
    "  - ''",
    "  - '&#94ff00☀ &fAmount: &#94ff00%amount%'",
    "  - '&#ff0000✎ &fSet By: &#ff0000%setby%'",
    'Rules:',
    '- Small-font bracket labels, like [ʙᴏᴜɴᴛʏ], using the phonetic small-caps alphabet. Leave s and x as Latin letters.',
    '- A Description section whose body lines start with a coloured pipe.',
    '- Hex &#RRGGBB mixed with &f and &7. Use the requested accent and keep a lime highlight for amounts when the source has them.',
    '- Fitting symbols such as ☀ for amounts and ✎ for who set the bounty.',
    '- Preserve placeholders exactly (%player%, %amount%, %setby%, and any other %tokens% or {tokens}).',
    '- Keep unrelated keys (material, textures, commands, prices) intact.',
    '- Do not invent placeholders that were not in the source or instruction.',
  ].join('\n');

  const response = await fetch(endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      model,
      temperature: 0.2,
      messages: [
        { role: 'system', content: system },
        {
          role: 'user',
          content: `Accent: ${accent}\nHighlight: ${secondary}\nInstruction: ${instruction}\n\nConfig:\n${yamlText}`,
        },
      ],
    }),
    signal: AbortSignal.timeout(45_000),
  });

  const bodyText = await response.text();
  if (!response.ok) {
    throw new Error(`Model request failed (${response.status}): ${bodyText.slice(0, 280)}`);
  }
  let parsed;
  try {
    parsed = JSON.parse(bodyText);
  } catch {
    throw new Error('Model response was not JSON');
  }
  const content = parsed?.choices?.[0]?.message?.content;
  if (typeof content !== 'string' || !content.trim()) {
    throw new Error('Model returned an empty response');
  }
  return content;
});

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    const win = BrowserWindow.getAllWindows()[0];
    if (!win) return;
    if (win.isMinimized()) win.restore();
    win.focus();
  });

  app.whenReady().then(() => {
    Menu.setApplicationMenu(null);
    createWindow();
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
  });
}
