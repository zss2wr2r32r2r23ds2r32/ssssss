import { app, BrowserWindow, screen } from 'electron'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import type { CrosshairSettings } from '../../shared/types'

let overlay: BrowserWindow | null = null

function overlayPreload(): string {
  const candidates = ['overlay.js', 'overlay.mjs', 'overlay.cjs'].map((file) =>
    join(__dirname, '../preload', file)
  )
  return candidates.find((file) => existsSync(file)) ?? candidates[0]
}

function overlayUrl(): string {
  if (!app.isPackaged && process.env.ELECTRON_RENDERER_URL) {
    return `${process.env.ELECTRON_RENDERER_URL}/overlay.html`
  }
  return `file://${join(__dirname, '../renderer/overlay.html')}`
}

function gamingDisplay() {
  const primary = screen.getPrimaryDisplay()
  return primary
}

function applyBounds(win: BrowserWindow): void {
  const display = gamingDisplay()
  win.setBounds({
    x: display.bounds.x,
    y: display.bounds.y,
    width: display.bounds.width,
    height: display.bounds.height
  })
}

export function isOverlayOpen(): boolean {
  return overlay !== null && !overlay.isDestroyed()
}

export async function startOverlay(settings: CrosshairSettings): Promise<void> {
  if (isOverlayOpen()) {
    updateOverlay(settings)
    return
  }
  const display = gamingDisplay()
  overlay = new BrowserWindow({
    x: display.bounds.x,
    y: display.bounds.y,
    width: display.bounds.width,
    height: display.bounds.height,
    frame: false,
    transparent: true,
    backgroundColor: '#00000000',
    hasShadow: false,
    skipTaskbar: true,
    resizable: false,
    movable: false,
    focusable: false,
    fullscreenable: false,
    alwaysOnTop: true,
    show: false,
    webPreferences: {
      preload: overlayPreload(),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true
    }
  })
  overlay.setAlwaysOnTop(true, 'screen-saver')
  overlay.setIgnoreMouseEvents(true, { forward: true })
  overlay.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true })
  try {
    overlay.setContentProtection(true)
  } catch {
    // Exclude-from-capture is best-effort.
  }
  overlay.on('closed', () => {
    overlay = null
  })
  await overlay.loadURL(overlayUrl())
  overlay.showInactive()
  overlay.webContents.send('overlay:settings', settings)
}

export function updateOverlay(settings: CrosshairSettings): void {
  if (!isOverlayOpen() || !overlay) return
  overlay.webContents.send('overlay:settings', settings)
}

export function stopOverlay(): void {
  if (overlay && !overlay.isDestroyed()) {
    overlay.close()
  }
  overlay = null
}

export function recenterOverlay(): void {
  if (overlay && !overlay.isDestroyed()) {
    applyBounds(overlay)
  }
}

export function attachDisplayListeners(): void {
  screen.on('display-metrics-changed', recenterOverlay)
  screen.on('display-added', recenterOverlay)
  screen.on('display-removed', recenterOverlay)
}
