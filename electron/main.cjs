const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const http = require('http');
const { spawn } = require('child_process');

const PORT = process.env.PORT || '4177';
let serverProcess = null;

function iconPath() {
  if (app.isPackaged) return path.join(process.resourcesPath, 'ui', 'logo.png');
  return path.join(__dirname, '..', 'launcher', 'public', 'logo.png');
}

function startPackagedServer() {
  const entry = path.join(process.resourcesPath, 'server.cjs');
  const env = {
    ...process.env,
    ELECTRON_RUN_AS_NODE: '1',
    PORT,
    NEXA_STATIC: path.join(process.resourcesPath, 'ui'),
    NEXA_DATA_DIR: path.join(app.getPath('userData'), 'data'),
  };
  serverProcess = spawn(process.execPath, [entry], { env, stdio: 'inherit' });
  serverProcess.on('exit', (code) => {
    if (code && code !== 0) console.error(`[nexa] API exited with ${code}`);
  });
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
  if (app.isPackaged) win.loadURL(`http://127.0.0.1:${PORT}/`);
  else win.loadURL(process.env.NEXA_DEV_URL || 'http://localhost:5173/');

  ipcMain.on('window:minimize', () => win.minimize());
  ipcMain.on('window:maximize', () => {
    if (win.isMaximized()) win.unmaximize();
    else win.maximize();
  });
  ipcMain.on('window:close', () => win.close());
}

app.whenReady().then(async () => {
  if (app.isPackaged) {
    startPackagedServer();
    await waitForServer();
  }
  createWindow();
});

app.on('window-all-closed', () => {
  if (serverProcess) serverProcess.kill();
  app.quit();
});
