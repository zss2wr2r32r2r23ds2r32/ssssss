import type {
  AppConfig,
  AvatarSettings,
  CleanupCandidate,
  CrosshairSettings,
  DisplayInfo,
  FortniteInstallInfo,
  GpuInfo,
  LaunchResult,
  LaunchStatus,
  MacroSettings,
  MonitorSnapshot,
  OperationResult,
  ResolutionSettings,
  ScrimSettings,
  SkinPreview,
  StatusEvent,
  ToastPayload
} from '../../../shared/types'

function invoke<T>(channel: string, payload?: unknown): Promise<T> {
  if (!window.nautical) {
    throw new Error('Avix preload bridge is unavailable.')
  }
  return window.nautical.invoke(channel, payload) as Promise<T>
}

export const api = {
  getConfig: () => invoke<AppConfig>('config:get'),
  updateConfig: (patch: Partial<AppConfig>) => invoke<AppConfig>('config:update', patch),
  createProfile: (name: string) => invoke<AppConfig>('profiles:create', { name }),
  updateProfile: (id: string, patch: Record<string, unknown>) => invoke<AppConfig>('profiles:update', { id, ...patch }),
  deleteProfile: (id: string) => invoke<OperationResult<AppConfig>>('profiles:delete', { id }),
  duplicateProfile: (id: string) => invoke<AppConfig>('profiles:duplicate', { id }),
  setActiveProfile: (id: string) => invoke<AppConfig>('profiles:set-active', { id }),
  setDefaultProfile: (id: string) => invoke<AppConfig>('profiles:set-default', { id }),
  detectFortnite: () => invoke<FortniteInstallInfo>('fortnite:detect'),
  browseFortnite: () => invoke<FortniteInstallInfo>('fortnite:browse'),
  validateFortnite: (path: string) => invoke<FortniteInstallInfo>('fortnite:validate', { path }),
  launch: () => invoke<LaunchResult>('fortnite:launch'),
  status: () => invoke<LaunchStatus>('fortnite:status'),
  displays: () => invoke<DisplayInfo[]>('resolution:info'),
  gpu: () => invoke<{ gpu: GpuInfo; guidance: string[] }>('resolution:gpu'),
  applyResolution: (settings: ResolutionSettings, permanent: boolean) =>
    invoke<OperationResult>('resolution:apply', { settings, permanent }),
  restoreNative: () => invoke<OperationResult>('resolution:restore-native'),
  testResolution: (width: number, height: number) => invoke<OperationResult>('resolution:test', { width, height }),
  backupConfig: () => invoke<OperationResult<{ backupPath: string | null }>>('resolution:backup-config'),
  startOverlay: () => invoke<OperationResult>('crosshair:start'),
  stopOverlay: () => invoke<OperationResult>('crosshair:stop'),
  updateCrosshair: (settings: CrosshairSettings) => invoke<AppConfig>('crosshair:update', settings),
  importCrosshairImage: () => invoke<OperationResult<AppConfig>>('crosshair:import-image'),
  saveCrosshairPreset: (name: string) => invoke<AppConfig>('crosshair:save-preset', { name }),
  deleteCrosshairPreset: (id: string) => invoke<AppConfig>('crosshair:delete-preset', { id }),
  candidates: () => invoke<CleanupCandidate[]>('performance:candidates'),
  cleanup: (ids: string[]) => invoke<OperationResult>('performance:cleanup', { ids }),
  restoreCleanup: () => invoke<OperationResult>('performance:restore'),
  startMacro: () => invoke<OperationResult>('macro:start'),
  stopMacro: () => invoke<OperationResult>('macro:stop'),
  updateMacro: (settings: MacroSettings) => invoke<AppConfig>('macro:update', settings),
  monitor: () => invoke<MonitorSnapshot>('monitor:snapshot'),
  minimize: () => invoke<void>('window:minimize'),
  maximize: () => invoke<void>('window:maximize'),
  close: () => invoke<void>('window:close'),
  openExternal: (url: string) => invoke<OperationResult>('window:open-external', { url }),
  detectSkin: () => invoke<SkinPreview>('skin:detect'),
  importSkin: () => invoke<OperationResult<SkinPreview>>('skin:import'),
  updateSkin: (skin: SkinPreview) => invoke<AppConfig>('skin:update', skin),
  getAvatar: () => invoke<string | null>('avatar:get'),
  importAvatar: () => invoke<OperationResult<{ avatar: AvatarSettings; image: string }>>('avatar:import'),
  clearAvatar: () => invoke<OperationResult<AvatarSettings>>('avatar:clear'),
  appInfo: () => invoke<{ configPath: string; userData: string; version: string }>('app:info'),
  openUpdates: (url?: string) => invoke<OperationResult>('updates:open', url ? { url } : {}),
  listScrims: () => invoke<ScrimSettings>('scrims:list'),
  updateScrims: (scrims: ScrimSettings) => invoke<ScrimSettings>('scrims:update', scrims),
  refreshScrims: () => invoke<ScrimSettings>('scrims:refresh'),
  testScrim: (id: string) => invoke<OperationResult>('scrims:test', { id }),
  applyGeneral: (general: AppConfig['general']) => invoke<AppConfig>('settings:apply-general', general),
  completeWizard: () => invoke<AppConfig>('wizard:complete'),
  checkUpdates: () =>
    invoke<{
      ok: boolean
      current: string
      latest: string | null
      newer: boolean
      downloadUrl: string | null
      releasesUrl: string
      message: string
    }>('updates:check')
}

export function onStatus(listener: (event: StatusEvent) => void): () => void {
  return window.nautical.on('status:changed', (payload) => listener(payload as StatusEvent))
}

export function onToast(listener: (toast: ToastPayload) => void): () => void {
  return window.nautical.on('toast:show', (payload) => listener(payload as ToastPayload))
}

export function onConfig(listener: (config: AppConfig) => void): () => void {
  return window.nautical.on('config:changed', (payload) => listener(payload as AppConfig))
}
