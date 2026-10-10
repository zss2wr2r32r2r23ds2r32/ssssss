const { app, BrowserWindow, dialog, ipcMain, shell } = require('electron');
const path = require('path');
const http = require('http');

const PORT = String(process.env.PORT || '4177');
process.env.PORT = PORT;

if (process.env.NEXA_USER_DATA) {
  app.setPath('userData', process.env.NEXA_USER_DATA);
}
if (process.env.NEXA_DEBUG_PORT) {
  app.commandLine.appendSwitch('remote-debugging-port', process.env.NEXA_DEBUG_PORT);
  app.commandLine.appendSwitch('remote-allow-origins', '*');
}

function packagedMode() {
  return app.isPackaged || process.env.NEXA_PACKAGED_TEST === '1';
}

function resourcesRoot() {
  if (app.isPackaged) return process.resourcesPath;
  if (process.env.NEXA_RESOURCES) return process.env.NEXA_RESOURCES;
  return path.join(__dirname, '..');
}

function iconPath() {
  if (packagedMode()) return path.join(resourcesRoot(), 'ui', 'logo.png');
  return path.join(__dirname, '..', 'launcher', 'public', 'logo.png');
}

function startPackagedServer() {
  const root = resourcesRoot();
  process.env.PORT = PORT;
  process.env.NEXA_STATIC = path.join(root, 'ui');
  process.env.NEXA_DATA_DIR = path.join(app.getPath('userData'), 'data');
  const entry = path.join(root, 'server.cjs');
  // Load the API in this process. Spawning process.execPath fails on the
  // portable Windows stub, which is not a Node binary.
  require(entry);
}

function waitForServer() {
  return new Promise((resolve, reject) => {
    const started = Date.now();
    const tick = () => {
      const req = http.get(`http://127.0.0.1:${PORT}/health`, (res) => {
        res.resume();
        if (res.statusCode === 200) resolve();
        else retry();
      });
      req.on('error', retry);
    };
    const retry = () => {
      if (Date.now() - started > 20000) reject(new Error('Nexa API did not start'));
      else setTimeout(tick, 200);
    };
    tick();
  });
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 1100,
    minHeight: 700,
    title: 'Nexa',
    backgroundColor: '#07080c',
    frame: false,
    autoHideMenuBar: true,
    icon: iconPath(),
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  win.setTitle('Nexa');
  if (packagedMode()) win.loadURL(`http://127.0.0.1:${PORT}/`);
  else win.loadURL(process.env.NEXA_DEV_URL || 'http://localhost:5173/');

  ipcMain.on('window:minimize', () => win.minimize());
  ipcMain.on('window:maximize', () => {
    if (win.isMaximized()) win.unmaximize();
    else win.maximize();
  });
  ipcMain.on('window:close', () => win.close());
}

ipcMain.handle('shell:open-external', async (_event, url) => {
  if (typeof url !== 'string' || !url.startsWith('https://discord.com/oauth2/authorize?')) {
    throw new Error('Refusing to open that address.');
  }
  await shell.openExternal(url);
});

app.whenReady().then(async () => {
  if (packagedMode()) {
    try {
      startPackagedServer();
      await waitForServer();
    } catch (error) {
      dialog.showErrorBox('Nexa', error instanceof Error ? error.message : 'The local Nexa API did not start.');
      app.quit();
      return;
    }
  }
  createWindow();
});

app.on('window-all-closed', () => {
  app.quit();
});
