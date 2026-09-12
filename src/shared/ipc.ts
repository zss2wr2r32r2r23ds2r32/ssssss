export const IPC_CHANNELS = [
  'config:get',
  'config:update',
  'profiles:create',
  'profiles:update',
  'profiles:delete',
  'profiles:duplicate',
  'profiles:set-active',
  'profiles:set-default',
  'fortnite:detect',
  'fortnite:browse',
  'fortnite:validate',
  'fortnite:set-path',
  'fortnite:launch',
  'fortnite:status',
  'resolution:info',
  'resolution:gpu',
  'resolution:apply',
  'resolution:restore-native',
  'resolution:test',
  'resolution:backup-config',
  'crosshair:start',
  'crosshair:stop',
  'crosshair:update',
  'crosshair:import-image',
  'crosshair:save-preset',
  'crosshair:delete-preset',
  'performance:candidates',
  'performance:cleanup',
  'performance:restore',
  'macro:start',
  'macro:stop',
  'macro:update',
  'monitor:snapshot',
  'window:minimize',
  'window:maximize',
  'window:close',
  'window:open-external',
  'skin:detect',
  'skin:import',
  'skin:update',
  'scrims:list',
  'scrims:update',
  'scrims:refresh',
  'scrims:test',
  'settings:apply-general',
  'wizard:complete',
  'updates:check'
] as const

export type IpcChannel = (typeof IPC_CHANNELS)[number]

export const IPC_EVENTS = [
  'status:changed',
  'toast:show',
  'config:changed',
  'overlay:settings',
  'monitor:tick'
] as const

export type IpcEvent = (typeof IPC_EVENTS)[number]

export const ALLOWED_EXTERNAL_HOSTS = [
  'discord.com',
  'discord.gg',
  'canary.discord.com',
  'ptb.discord.com'
] as const

export function isAllowedExternalUrl(url: string): boolean {
  try {
    const parsed = new URL(url)
    if (parsed.protocol !== 'https:') return false
    return (ALLOWED_EXTERNAL_HOSTS as readonly string[]).includes(parsed.hostname)
  } catch {
    return false
  }
}

export function isDiscordWebhookUrl(url: string): boolean {
  try {
    const parsed = new URL(url)
    if (parsed.protocol !== 'https:') return false
    const hostOk = parsed.hostname === 'discord.com' || parsed.hostname === 'discordapp.com'
    return hostOk && /^\/api\/webhooks\/\d+\/[A-Za-z0-9_-]+$/.test(parsed.pathname)
  } catch {
    return false
  }
}

export function isIpcChannel(value: string): value is IpcChannel {
  return (IPC_CHANNELS as readonly string[]).includes(value)
}
