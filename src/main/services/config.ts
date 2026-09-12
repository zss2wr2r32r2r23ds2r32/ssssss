import { app } from 'electron'
import { closeSync, existsSync, fsyncSync, mkdirSync, openSync, readFileSync, writeSync } from 'node:fs'
import path from 'node:path'
import { createDefaultConfig, getActiveProfile } from '../../shared/defaults'
import type { AppConfig, Profile } from '../../shared/types'
import { APP_VERSION } from '../../shared/types'

let cached: AppConfig | null = null

export function configPath(): string {
  return path.join(app.getPath('userData'), 'avix-config.json')
}

function legacyConfigPath(): string {
  return path.join(app.getPath('userData'), 'nautical-config.json')
}

export function backupsDir(): string {
  const dir = path.join(app.getPath('userData'), 'backups')
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true })
  return dir
}

function deepMerge<T>(base: T, incoming: Partial<T>): T {
  if (Array.isArray(incoming)) return incoming as T
  if (incoming && typeof incoming === 'object' && base && typeof base === 'object') {
    const out = { ...base } as Record<string, unknown>
    for (const [key, value] of Object.entries(incoming)) {
      const current = (base as Record<string, unknown>)[key]
      if (value && typeof value === 'object' && !Array.isArray(value) && current && typeof current === 'object') {
        out[key] = deepMerge(current, value as never)
      } else if (value !== undefined) {
        out[key] = value
      }
    }
    return out as T
  }
  return (incoming as T) ?? base
}

export function loadConfig(): AppConfig {
  if (cached) return cached
  const file = existsSync(configPath()) ? configPath() : legacyConfigPath()
  const fallback = createDefaultConfig()
  if (!existsSync(file)) {
    cached = fallback
    saveConfig(cached)
    return cached
  }
  try {
    const raw = JSON.parse(readFileSync(file, 'utf8')) as Partial<AppConfig>
    const merged = deepMerge(fallback, raw)
    merged.version = APP_VERSION
    if (!merged.profiles?.length) {
      merged.profiles = fallback.profiles
      merged.activeProfileId = fallback.activeProfileId
      merged.defaultProfileId = fallback.defaultProfileId
    }
    if (!merged.profiles.some((p) => p.id === merged.activeProfileId)) {
      merged.activeProfileId = merged.profiles[0].id
    }
    if (!merged.profiles.some((p) => p.id === merged.defaultProfileId)) {
      merged.defaultProfileId = merged.profiles[0].id
    }
    if (!merged.skin) merged.skin = fallback.skin
    if (!merged.avatar) merged.avatar = fallback.avatar
    if (!merged.scrims) merged.scrims = fallback.scrims
    if (!merged.general.launchMethod) merged.general.launchMethod = fallback.general.launchMethod
    if (merged.appearance?.accent?.toLowerCase() === '#3ee0ff') {
      merged.appearance.accent = '#FF4D9D'
    }
    cached = merged
    return cached
  } catch {
    cached = fallback
    return cached
  }
}

export function saveConfig(next: AppConfig): AppConfig {
  cached = next
  const file = configPath()
  mkdirSync(path.dirname(file), { recursive: true })
  const payload = `${JSON.stringify(next, null, 2)}\n`
  const fd = openSync(file, 'w')
  try {
    writeSync(fd, payload, 0, 'utf8')
    fsyncSync(fd)
  } finally {
    closeSync(fd)
  }
  return cached
}

export function flushConfig(): AppConfig {
  return saveConfig(loadConfig())
}

export function updateConfig(partial: Record<string, unknown> | Partial<AppConfig>): AppConfig {
  const current = loadConfig()
  return saveConfig(deepMerge(current, partial))
}

export function replaceProfiles(profiles: Profile[], extra: Partial<AppConfig> = {}): AppConfig {
  const current = loadConfig()
  return saveConfig({ ...current, ...extra, profiles })
}

export function activeProfile(): Profile {
  return getActiveProfile(loadConfig())
}

export function updateActiveProfile(mutator: (profile: Profile) => Profile): AppConfig {
  const config = loadConfig()
  const profiles = config.profiles.map((profile) =>
    profile.id === config.activeProfileId
      ? { ...mutator(profile), updatedAt: new Date().toISOString() }
      : profile
  )
  return saveConfig({ ...config, profiles })
}

export function resetConfigCache(): void {
  cached = null
}
