import { BrowserWindow, Menu, Tray, app, nativeImage, screen } from 'electron'
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { APP_NAME, APP_VERSION } from '../shared/types'
import { registerIpc } from './ipc/register'
import { applySessionGuards } from './security'
import { loadConfig } from './services/config'
import { attachDisplayListeners } from './services/overlay'
import { detectFortniteInstall, onStatus } from './services/fortnite'
import { launchFromActiveProfile } from './services/launcher'
import { onScrimAlert, startScrimPoller } from './services/scrims'
import { applyLoginItem, shouldStartHidden } from './services/settings-os'
import { isAppQuitting, markQuitting, requestQuit } from './quit'

let mainWindow: BrowserWindow | null = null
let tray: Tray | null = null

function resolvePreload(name: string): string {
  const candidates = [`${name}.js`, `${name}.mjs`, `${name}.cjs`].map((file) =>
    join(__dirname, '../preload', file)
  )
  return candidates.find((file) => existsSync(file)) ?? candidates[0]
}

function preloadPath(): string {
  return resolvePreload('index')
}

function rendererUrl(): string {
  if (!app.isPackaged && process.env.ELECTRON_RENDERER_URL) {
    return process.env.ELECTRON_RENDERER_URL
  }
  return `file://${join(__dirname, '../renderer/index.html')}`
}

function iconPath(): string | null {
  const candidates = [
    join(process.resourcesPath, 'resources', 'icon.png'),
    join(app.getAppPath(), 'resources', 'icon.png'),
    join(__dirname, '../../resources/icon.png')
  ]
  return candidates.find((file) => existsSync(file)) ?? null
}

function createWindow(): BrowserWindow {
  const primary = screen.getPrimaryDisplay().workAreaSize
  const width = Math.min(1320, Math.max(1100, primary.width - 80))
  const height = Math.min(860, Math.max(720, primary.height - 80))
  const icon = iconPath()
  const win = new BrowserWindow({
    width,
    height,
    minWidth: 1100,
    minHeight: 700,
    frame: false,
    backgroundColor: '#16141f',
    title: APP_NAME,
    icon: icon ?? undefined,
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload: preloadPath(),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      spellcheck: false
    }
  })

  win.on('ready-to-show', () => {
    if (!shouldStartHidden() && !loadConfig().general.startMinimized) {
      win.show()
    } else if (loadConfig().general.trayEnabled) {
      win.hide()
    } else {
      win.show()
    }
  })

  win.on('close', (event) => {
    if (loadConfig().general.closeToTray && !isAppQuitting()) {
      event.preventDefault()
      win.hide()
    }
  })

  void win.loadURL(rendererUrl())
  return win
}

function createTray(): void {
  if (tray) return
  const file = iconPath()
  const image = file ? nativeImage.createFromPath(file) : nativeImage.createEmpty()
  tray = new Tray(image.isEmpty() ? nativeImage.createFromDataURL(trayFallback()) : image)
  tray.setToolTip(`${APP_NAME} ${APP_VERSION}`)
  tray.setContextMenu(
    Menu.buildFromTemplate([
      { label: 'Open Avix', click: () => mainWindow?.show() },
      {
        label: 'Launch Fortnite',
        click: () => {
          void launchFromActiveProfile()
        }
      },
      { type: 'separator' },
      {
        label: 'Quit',
        click: () => {
          requestQuit()
        }
      }
    ])
  )
  tray.on('click', () => mainWindow?.show())
}

function trayFallback(): string {
  return 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAMAAABEpIrGAAAANlBMVEUAAAAAOlr///8/q8RBb5NAtM5Db5lCbpVAtM5CbpVCbpVAtM5CbpX///9CbpVAtM5CbpX////09PQAOlrX0n0IAAAAEnRSTlMAECAwQFBgcICPn6+/z9/vIxq9owAAAIRJREFUOMvd0ckOgCAMBNBhX1H6/392E4mJ2IJePJvJSwetAKjUqFGj/gHMzMwAZmYGmJmZAWZmZoCZmRlgZmYGmJmZAWZmZoCZmRlgZmYGmJmZAWZmZoCZmRlgZmYGmJmZAWZmZoCZmRlgZmYGmJmZAWZmZoCZmRlgZmYGmJmZ+QPzA7gB3gQhC0c7zQwAAAAASUVORK5CYII='
}

const gotLock = app.requestSingleInstanceLock()
if (!gotLock) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore()
      mainWindow.show()
      mainWindow.focus()
    }
  })

  const earlyConfig = (() => {
    try {
      const userData = app.getPath('userData')
      const file = existsSync(join(userData, 'avix-config.json'))
        ? join(userData, 'avix-config.json')
        : join(userData, 'nautical-config.json')
      if (!existsSync(file)) return null
      return JSON.parse(readFileSync(file, 'utf8')) as { general?: { hardwareAcceleration?: boolean } }
    } catch {
      return null
    }
  })()

  if (earlyConfig?.general?.hardwareAcceleration === false) {
    app.disableHardwareAcceleration()
  }

  app.whenReady().then(async () => {
    applySessionGuards()
    Menu.setApplicationMenu(null)
    registerIpc(() => mainWindow)
    attachDisplayListeners()
    mainWindow = createWindow()
    const config = loadConfig()
    applyLoginItem(config.general)
    if (config.general.trayEnabled) createTray()
    startScrimPoller()
    onScrimAlert((title, body) => {
      mainWindow?.webContents.send('toast:show', {
        id: `scrim-${Date.now()}`,
        tone: 'warn',
        title,
        body
      })
    })

    onStatus((event) => {
      mainWindow?.webContents.send('status:changed', event)
      if (event.message) {
        mainWindow?.webContents.send('toast:show', {
          id: `status-${Date.now()}`,
          tone: event.unexpected ? 'warn' : 'info',
          title: event.unexpected ? 'Fortnite closed unexpectedly' : 'Fortnite status',
          body: event.message
        })
      }
    })

    void detectFortniteInstall()
    if (config.general.autoLaunchFortnite && config.wizardCompleted) {
      setTimeout(() => {
        void launchFromActiveProfile()
      }, 1500)
    }
  })

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
      if (!loadConfig().general.trayEnabled) app.quit()
    }
  })

  app.on('before-quit', () => {
    markQuitting()
  })

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      mainWindow = createWindow()
    } else {
      mainWindow?.show()
    }
  })
}
