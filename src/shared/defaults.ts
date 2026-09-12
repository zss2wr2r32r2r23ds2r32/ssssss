import {
  APP_VERSION,
  type AppConfig,
  type CrosshairSettings,
  type FortniteGraphicsSettings,
  type AvatarSettings,
  type GeneralSettings,
  type MacroSettings,
  type PerformanceSettings,
  type Profile,
  type ResolutionSettings,
  type ScrimSettings,
  type SkinPreview
} from './types'

export const DEFAULT_DISCORD_URL = 'https://discord.com/invite/fortnite'

export const DEFAULT_CROSSHAIR: CrosshairSettings = {
  enabled: false,
  onlyWhileFortnite: true,
  presetId: 'classic-dot',
  color: '#FF4D9D',
  opacity: 0.95,
  size: 4,
  thickness: 2,
  gap: 3,
  outline: true,
  outlineThickness: 1,
  outlineColor: '#061018',
  showDot: true,
  dotSize: 4,
  horizontal: false,
  vertical: false,
  rotation: 0,
  shape: 'dot',
  centerGap: false,
  customImage: null,
  customImageName: null
}

export const DEFAULT_RESOLUTION: ResolutionSettings = {
  width: 1920,
  height: 1080,
  method: 'display',
  applyOnLaunch: true,
  temporary: true,
  applyGameUserSettings: true
}

export const DEFAULT_GRAPHICS: FortniteGraphicsSettings = {
  fullscreen: true,
  vsync: false,
  performanceMode: true,
  lowGraphics: true,
  fpsLimit: 'unlimited'
}

export const DEFAULT_PERFORMANCE: PerformanceSettings = {
  cleanupOnLaunch: false,
  restoreAfterExit: true,
  selectedApps: []
}

export const DEFAULT_MACRO: MacroSettings = {
  enabled: false,
  acknowledgedRisk: false,
  key: 'E',
  intervalSec: 0.25,
  mode: 'fortnite-focus',
  activationKey: 'F8',
  onlyWhileFortniteFocused: true,
  mouseButton: 'none'
}

export const DEFAULT_GENERAL: GeneralSettings = {
  startWithWindows: false,
  trayEnabled: false,
  closeToTray: false,
  startMinimized: false,
  autoLaunchFortnite: false,
  checkUpdates: true,
  hardwareAcceleration: true,
  discordUrl: DEFAULT_DISCORD_URL,
  displayName: 'competitor',
  discordWebhookUrl: '',
  launchMethod: 'bootstrapper',
  hideEpicAfterLaunch: true
}

export const DEFAULT_AVATAR: AvatarSettings = {
  fileName: null,
  mime: null
}

export const DEFAULT_SKIN: SkinPreview = {
  name: null,
  image: null,
  source: 'placeholder',
  cosmeticId: null
}

export function seedScrims(): ScrimSettings {
  const names = ['Noble Elite', 'Poyo Elite', 'Ladder Elite', 'Duos Elite', 'Solos Elite']
  return {
    pollSeconds: 60,
    sources: names.map((name) => ({
      id: name.toLowerCase().replace(/\s+/g, '-'),
      name,
      enabled: name === 'Noble Elite',
      statusUrl: null,
      lastLiveAt: null
    }))
  }
}

export function nowIso(): string {
  return new Date().toISOString()
}

export function createId(prefix: string): string {
  return `${prefix}-${Math.random().toString(36).slice(2, 10)}-${Date.now().toString(36)}`
}

export function createProfile(name: string, overrides: Partial<Profile> = {}): Profile {
  const ts = nowIso()
  return {
    id: createId('profile'),
    name,
    createdAt: ts,
    updatedAt: ts,
    crosshair: { ...DEFAULT_CROSSHAIR },
    resolution: { ...DEFAULT_RESOLUTION },
    graphics: { ...DEFAULT_GRAPHICS },
    performance: { ...DEFAULT_PERFORMANCE },
    macro: { ...DEFAULT_MACRO },
    ...overrides
  }
}

export function seedProfiles(): Profile[] {
  const competitive = createProfile('Competitive', {
    graphics: {
      fullscreen: true,
      vsync: false,
      performanceMode: true,
      lowGraphics: true,
      fpsLimit: 'unlimited'
    },
    resolution: {
      ...DEFAULT_RESOLUTION,
      width: 1920,
      height: 1080,
      applyOnLaunch: true,
      method: 'display'
    },
    crosshair: {
      ...DEFAULT_CROSSHAIR,
      enabled: true,
      presetId: 'precision-dot',
      shape: 'dot',
      size: 2,
      dotSize: 2,
      outline: true
    },
    performance: {
      cleanupOnLaunch: false,
      restoreAfterExit: true,
      selectedApps: ['discord', 'chrome', 'spotify']
    }
  })

  const aggressive = createProfile('Aggressive', {
    graphics: {
      fullscreen: true,
      vsync: false,
      performanceMode: true,
      lowGraphics: true,
      fpsLimit: 'unlimited'
    },
    resolution: {
      ...DEFAULT_RESOLUTION,
      width: 1728,
      height: 1080,
      applyOnLaunch: true,
      method: 'display'
    },
    crosshair: {
      ...DEFAULT_CROSSHAIR,
      enabled: true,
      presetId: 'small-cross',
      shape: 'cross',
      size: 10,
      thickness: 2,
      gap: 3,
      showDot: false,
      horizontal: true,
      vertical: true
    },
    performance: {
      cleanupOnLaunch: true,
      restoreAfterExit: true,
      selectedApps: ['discord', 'chrome', 'spotify', 'edge']
    }
  })

  const native = createProfile('Native', {
    graphics: {
      fullscreen: true,
      vsync: false,
      performanceMode: false,
      lowGraphics: false,
      fpsLimit: 160
    },
    resolution: {
      ...DEFAULT_RESOLUTION,
      width: 1920,
      height: 1080,
      applyOnLaunch: false,
      method: 'display',
      temporary: true
    },
    crosshair: {
      ...DEFAULT_CROSSHAIR,
      enabled: false
    },
    performance: {
      cleanupOnLaunch: false,
      restoreAfterExit: true,
      selectedApps: []
    }
  })

  return [competitive, aggressive, native]
}

export function createDefaultConfig(): AppConfig {
  const profiles = seedProfiles()
  return {
    version: APP_VERSION,
    wizardCompleted: false,
    fortnitePath: null,
    fortniteVersion: null,
    activeProfileId: profiles[0].id,
    defaultProfileId: profiles[0].id,
    profiles,
    savedCrosshairPresets: [],
    general: { ...DEFAULT_GENERAL },
    appearance: {
      theme: 'dark',
      accent: '#FF4D9D',
      transparency: 0.94,
      animationIntensity: 'full'
    },
    lastNativeResolution: null,
    skin: { ...DEFAULT_SKIN },
    avatar: { ...DEFAULT_AVATAR },
    scrims: seedScrims()
  }
}

export function getActiveProfile(config: AppConfig): Profile {
  return (
    config.profiles.find((p) => p.id === config.activeProfileId) ??
    config.profiles.find((p) => p.id === config.defaultProfileId) ??
    config.profiles[0]
  )
}
