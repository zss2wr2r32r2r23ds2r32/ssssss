import { app, BrowserWindow, screen } from 'electron'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { DEFAULT_CROSSHAIR } from '../../shared/defaults'
import { centerOverlayOnRect, overlayMarkSize, rectFromCorners } from '../../shared/overlay-center'
import type { CrosshairSettings } from '../../shared/types'
import { hostFortniteRect } from './win32-host'

let overlay: BrowserWindow | null = null
let lastSettings: CrosshairSettings = DEFAULT_CROSSHAIR
const recenterTimeouts = new Set<NodeJS.Timeout>()

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
  try {
    return screen.getDisplayNearestPoint(screen.getCursorScreenPoint())
  } catch {
    return screen.getPrimaryDisplay()
  }
}

async function computePlacement(): Promise<{ x: number; y: number; width: number; height: number }> {
  const mark = overlayMarkSize(lastSettings.size)
  try {
    const raw = await hostFortniteRect()
    if (raw) {
      const topLeft = screen.screenToDipPoint({ x: raw.left, y: raw.top })
      const bottomRight = screen.screenToDipPoint({ x: raw.right, y: raw.bottom })
      return centerOverlayOnRect(rectFromCorners(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y), mark)
    }
  } catch {
    /* Fall back to the gaming display. */
  }
  const display = gamingDisplay()
  return centerOverlayOnRect(display.bounds, mark)
}

function clearRecenterTimers(): void {
  for (const timer of recenterTimeouts) clearTimeout(timer)
  recenterTimeouts.clear()
}

export function isOverlayOpen(): boolean {
  return overlay !== null && !overlay.isDestroyed()
}

export async function startOverlay(settings: CrosshairSettings): Promise<void> {
  lastSettings = settings
  if (isOverlayOpen()) {
    updateOverlay(settings)
    await recenterOverlay()
    return
  }
  const bounds = await computePlacement()
  overlay = new BrowserWindow({
    x: bounds.x,
    y: bounds.y,
    width: bounds.width,
    height: bounds.height,
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
      webSecurity: true,
      backgroundThrottling: true
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
    clearRecenterTimers()
  })
  await overlay.loadURL(overlayUrl())
  overlay.showInactive()
  overlay.webContents.send('overlay:settings', settings)
  scheduleRecenter()
}

export function updateOverlay(settings: CrosshairSettings): void {
  lastSettings = settings
  if (!isOverlayOpen() || !overlay) return
  overlay.webContents.send('overlay:settings', settings)
  void recenterOverlay()
}

export function stopOverlay(): void {
  clearRecenterTimers()
  if (overlay && !overlay.isDestroyed()) {
    overlay.close()
  }
  overlay = null
}

export async function recenterOverlay(): Promise<void> {
  if (!overlay || overlay.isDestroyed()) return
  const bounds = await computePlacement()
  overlay.setBounds(bounds)
}

function scheduleRecenter(): void {
  clearRecenterTimers()
  for (const ms of [400, 1600, 4000]) {
    const timer = setTimeout(() => {
      recenterTimeouts.delete(timer)
      void recenterOverlay()
    }, ms)
    recenterTimeouts.add(timer)
  }
}

export function attachDisplayListeners(): void {
  screen.on('display-metrics-changed', () => {
    void recenterOverlay()
  })
  screen.on('display-added', () => {
    void recenterOverlay()
  })
  screen.on('display-removed', () => {
    void recenterOverlay()
  })
}
