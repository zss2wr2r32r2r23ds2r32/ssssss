export const APP_VERSION = '1.0.0'
export const APP_NAME = 'Nautical Fortnite Launcher'

export type LaunchStatus =
  | 'NOT_RUNNING'
  | 'LAUNCHING'
  | 'RUNNING'
  | 'CLOSING'
  | 'CLOSED'

export type NavPage =
  | 'home'
  | 'crosshair'
  | 'resolution'
  | 'performance'
  | 'macro'
  | 'settings'

export type GpuVendor = 'NVIDIA' | 'AMD' | 'Intel' | 'Unknown'
export type ScalingMethod = 'gpu' | 'display' | 'automatic' | 'fortnite-only'
export type MacroMode = 'hold' | 'toggle' | 'fortnite-focus'
export type AnimationIntensity = 'off' | 'subtle' | 'full'
export type ThemeId = 'dark'
export type CrosshairShape =
  | 'dot'
  | 'cross'
  | 'hollow-cross'
  | 't'
  | 'plus'
  | 'lines'
  | 'circle'
  | 'dot-circle'
  | 'custom-image'

export interface CrosshairSettings {
  enabled: boolean
  onlyWhileFortnite: boolean
  presetId: string
  color: string
  opacity: number
  size: number
  thickness: number
  gap: number
  outline: boolean
  outlineThickness: number
  outlineColor: string
  showDot: boolean
  dotSize: number
  horizontal: boolean
  vertical: boolean
  rotation: number
  shape: CrosshairShape
  centerGap: boolean
  customImage: string | null
  customImageName: string | null
}

export interface SavedCrosshairPreset {
  id: string
  name: string
  settings: Omit<CrosshairSettings, 'enabled' | 'onlyWhileFortnite' | 'customImage' | 'customImageName'>
}

export interface ResolutionSettings {
  width: number
  height: number
  method: ScalingMethod
  applyOnLaunch: boolean
  temporary: boolean
  applyGameUserSettings: boolean
}

export interface FortniteGraphicsSettings {
  fullscreen: boolean
  vsync: boolean
  performanceMode: boolean
  lowGraphics: boolean
  fpsLimit: number | 'unlimited'
}

export interface PerformanceSettings {
  cleanupOnLaunch: boolean
  restoreAfterExit: boolean
  selectedApps: string[]
}

export interface MacroSettings {
  enabled: boolean
  acknowledgedRisk: boolean
  key: string
  intervalSec: number
  mode: MacroMode
  activationKey: string
  onlyWhileFortniteFocused: boolean
  mouseButton: 'none' | 'left' | 'right' | 'middle'
}

export interface Profile {
  id: string
  name: string
  createdAt: string
  updatedAt: string
  crosshair: CrosshairSettings
  resolution: ResolutionSettings
  graphics: FortniteGraphicsSettings
  performance: PerformanceSettings
  macro: MacroSettings
}

export interface GeneralSettings {
  startWithWindows: boolean
  trayEnabled: boolean
  startMinimized: boolean
  autoLaunchFortnite: boolean
  checkUpdates: boolean
  hardwareAcceleration: boolean
  discordUrl: string
}

export interface AppearanceSettings {
  theme: ThemeId
  accent: string
  transparency: number
  animationIntensity: AnimationIntensity
}

export interface NativeResolution {
  width: number
  height: number
  savedAt: string
}

export interface AppConfig {
  version: string
  wizardCompleted: boolean
  fortnitePath: string | null
  fortniteVersion: string | null
  activeProfileId: string
  defaultProfileId: string
  profiles: Profile[]
  savedCrosshairPresets: SavedCrosshairPreset[]
  general: GeneralSettings
  appearance: AppearanceSettings
  lastNativeResolution: NativeResolution | null
}

export interface FortniteInstallInfo {
  found: boolean
  path: string | null
  version: string | null
  valid: boolean
  source: 'config' | 'auto' | 'browse' | null
  reason?: string
}

export interface DisplayInfo {
  id: number
  label: string
  bounds: { x: number; y: number; width: number; height: number }
  workArea: { x: number; y: number; width: number; height: number }
  scaleFactor: number
  primary: boolean
  currentWidth: number
  currentHeight: number
  availableModes: Array<{ width: number; height: number }>
}

export interface GpuInfo {
  vendor: GpuVendor
  name: string
  details: string[]
}

export interface CleanupCandidate {
  id: string
  name: string
  description: string
  processes: string[]
  running: boolean
  pids: number[]
  memoryMb: number
  protected: boolean
}

export interface MonitorSnapshot {
  cpuLoad: number | null
  cpuTemp: number | null
  gpuLoad: number | null
  gpuTemp: number | null
  gpuName: string | null
  ramUsedMb: number | null
  ramTotalMb: number | null
  resolution: { width: number; height: number } | null
  fortnite: {
    status: LaunchStatus
    pid: number | null
    uptimeSec: number | null
  }
  timestamp: number
}

export interface LaunchResult {
  ok: boolean
  status: LaunchStatus
  code?:
    | 'ALREADY_RUNNING'
    | 'MISSING_INSTALL'
    | 'INVALID_PATH'
    | 'APPLY_FAILED'
    | 'SPAWN_FAILED'
    | 'OVERLAY_FAILED'
    | 'MACRO_FAILED'
    | 'ADMIN_REQUIRED'
    | 'MACRO_NOT_ACKNOWLEDGED'
  message: string
}

export interface OperationResult<T = undefined> {
  ok: boolean
  message: string
  code?: string
  data?: T
}

export interface ToastPayload {
  id: string
  tone: 'info' | 'success' | 'warn' | 'error'
  title: string
  body?: string
}

export interface StatusEvent {
  status: LaunchStatus
  pid: number | null
  unexpected: boolean
  message?: string
}

export const RESOLUTION_PRESETS = [
  { id: '1920x1080', width: 1920, height: 1080, label: '1920×1080 Native', note: 'Standard 16:9 example' },
  { id: '1728x1080', width: 1728, height: 1080, label: '1728×1080', note: 'Example stretched width' },
  { id: '1600x1080', width: 1600, height: 1080, label: '1600×1080', note: 'Example stretched width' },
  { id: '1620x1080', width: 1620, height: 1080, label: '1620×1080', note: 'Example stretched width' },
  { id: '1500x1080', width: 1500, height: 1080, label: '1500×1080', note: 'Example stretched width' },
  { id: '1440x1080', width: 1440, height: 1080, label: '1440×1080', note: 'Example 4:3-like stretch' }
] as const

export const MACRO_INTERVAL_PRESETS = [0.1, 0.15, 0.2, 0.25, 0.33, 0.5, 0.75, 1, 1.5, 2] as const

export const ALLOWED_MACRO_KEYS = [
  ...'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split(''),
  ...'0123456789'.split(''),
  'F1', 'F2', 'F3', 'F4', 'F5', 'F6', 'F7', 'F8', 'F9', 'F10', 'F11', 'F12',
  'Space', 'Tab', 'Shift', 'Ctrl', 'Alt'
] as const

export const CROSSHAIR_PRESETS: Array<{
  id: string
  name: string
  settings: Partial<CrosshairSettings>
}> = [
  { id: 'classic-dot', name: 'Classic Dot', settings: { shape: 'dot', size: 4, showDot: true, dotSize: 4, thickness: 0, gap: 0, horizontal: false, vertical: false } },
  { id: 'small-cross', name: 'Small Cross', settings: { shape: 'cross', size: 10, thickness: 2, gap: 3, showDot: false, horizontal: true, vertical: true } },
  { id: 'hollow-cross', name: 'Hollow Cross', settings: { shape: 'hollow-cross', size: 14, thickness: 2, gap: 4, outline: true, showDot: false, horizontal: true, vertical: true } },
  { id: 't-cross', name: 'T Cross', settings: { shape: 't', size: 14, thickness: 2, gap: 3, showDot: false, horizontal: true, vertical: true } },
  { id: 'plus', name: 'Plus', settings: { shape: 'plus', size: 16, thickness: 2, gap: 0, showDot: false, horizontal: true, vertical: true } },
  { id: 'precision-dot', name: 'Precision Dot', settings: { shape: 'dot', size: 2, showDot: true, dotSize: 2, outline: true, outlineThickness: 1, horizontal: false, vertical: false } },
  { id: 'four-lines', name: 'Four Lines', settings: { shape: 'lines', size: 18, thickness: 2, gap: 6, showDot: false, horizontal: true, vertical: true } },
  { id: 'circle', name: 'Circle', settings: { shape: 'circle', size: 16, thickness: 2, gap: 0, showDot: false, horizontal: false, vertical: false } },
  { id: 'dot-circle', name: 'Dot + Circle', settings: { shape: 'dot-circle', size: 16, thickness: 2, showDot: true, dotSize: 3, gap: 0, horizontal: false, vertical: false } },
  { id: 'minimal', name: 'Minimal', settings: { shape: 'cross', size: 8, thickness: 1, gap: 2, showDot: false, outline: false, horizontal: true, vertical: true } }
]
