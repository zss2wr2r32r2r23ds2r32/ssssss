import { createDefaultConfig, getActiveProfile } from '../../../shared/defaults'
import { IPC_CHANNELS, IPC_EVENTS, isAllowedExternalUrl } from '../../../shared/ipc'
import { APP_NAME, APP_VERSION, type AppConfig, type LaunchStatus } from '../../../shared/types'

export function installMockBridge(): void {
  if (typeof navigator !== 'undefined' && /Electron/i.test(navigator.userAgent)) return
  if (!import.meta.env.DEV) return
  if (window.nautical) return
  let config = createDefaultConfig()
  let status: LaunchStatus = 'NOT_RUNNING'
  const listeners = new Map<string, Set<(payload: unknown) => void>>()

  const emit = (channel: string, payload: unknown) => {
    listeners.get(channel)?.forEach((fn) => fn(payload))
  }

  window.nautical = {
    channels: IPC_CHANNELS,
    events: IPC_EVENTS,
    invoke: async (channel, payload) => {
      switch (channel) {
        case 'config:get':
          return config
        case 'config:update':
          config = { ...config, ...(payload as object) } as AppConfig
          return config
        case 'fortnite:detect':
        case 'fortnite:browse':
          return {
            found: false,
            path: null,
            version: null,
            valid: false,
            source: 'auto',
            reason: 'Vite UI preview cannot scan Windows. Run the Electron app to detect Fortnite.'
          }
        case 'fortnite:launch':
          return { ok: false, status, code: 'MISSING_INSTALL', message: 'Vite UI preview cannot launch Fortnite. Use the Electron app.' }
        case 'fortnite:status':
          return status
        case 'resolution:info':
          return [{ id: 1, label: 'Preview', bounds: { x: 0, y: 0, width: 1920, height: 1080 }, workArea: { x: 0, y: 0, width: 1920, height: 1040 }, scaleFactor: 1, primary: true, currentWidth: 1920, currentHeight: 1080, availableModes: [{ width: 1920, height: 1080 }] }]
        case 'resolution:gpu':
          return { gpu: { vendor: 'Unknown', name: 'Preview GPU', details: [] }, guidance: ['Preview mode.'] }
        case 'performance:candidates':
          return []
        case 'monitor:snapshot':
          return {
            cpuLoad: 12,
            cpuTemp: null,
            gpuLoad: null,
            gpuTemp: null,
            gpuName: 'Preview',
            ramUsedMb: 8000,
            ramTotalMb: 16000,
            appCpu: 1,
            appRssMb: 180,
            resolution: { width: 1920, height: 1080 },
            fortnite: { status, pid: null, uptimeSec: null },
            timestamp: Date.now()
          }
        case 'wizard:complete':
          config = { ...config, wizardCompleted: true }
          return config
        case 'profiles:set-active':
          config = { ...config, activeProfileId: (payload as { id: string }).id }
          return config
        case 'profiles:set-default':
          config = { ...config, defaultProfileId: (payload as { id: string }).id }
          return config
        case 'profiles:create':
        case 'profiles:duplicate':
        case 'profiles:update':
        case 'crosshair:update':
        case 'macro:update':
        case 'settings:apply-general':
        case 'skin:update':
          return config
        case 'profiles:delete':
          return { ok: false, message: 'Preview keeps seeded profiles.' }
        case 'skin:detect':
          return config.skin
        case 'skin:import':
          return { ok: false, message: 'Vite UI preview cannot import files. Use the Electron app.' }
        case 'scrims:list':
        case 'scrims:refresh':
          return config.scrims
        case 'scrims:update':
          config = { ...config, scrims: payload as AppConfig['scrims'] }
          return config.scrims
        case 'scrims:test': {
          const id = (payload as { id: string }).id
          const source = config.scrims.sources.find((item) => item.id === id)
          const name = source?.name ?? 'scrim'
          emit('toast:show', { id: `preview-${Date.now()}`, tone: 'warn', title: 'Scrim alert', body: `Scrim is on — ${name}` })
          return { ok: true, message: `Alert fired for ${name}.` }
        }
        case 'window:minimize':
        case 'window:maximize':
        case 'window:close':
          return undefined
        case 'window:open-external': {
          const url = (payload as { url: string }).url
          if (!isAllowedExternalUrl(url)) return { ok: false, message: 'Only Discord HTTPS links.' }
          window.open(url, '_blank')
          return { ok: true, message: 'Opened Discord.' }
        }
        case 'updates:check':
          return { ok: true, current: APP_VERSION, latest: APP_VERSION, message: `You are running ${APP_NAME} ${APP_VERSION}.` }
        default:
          return { ok: true, message: 'Preview stub', data: getActiveProfile(config) }
      }
    },
    on: (channel, listener) => {
      const set = listeners.get(channel) ?? new Set()
      set.add(listener)
      listeners.set(channel, set)
      return () => {
        set.delete(listener)
      }
    }
  }
}
