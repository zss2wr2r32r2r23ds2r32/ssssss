import { BrowserWindow, dialog, ipcMain, shell, type OpenDialogOptions } from 'electron'
import { z } from 'zod'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { createId, createProfile, getActiveProfile } from '../../shared/defaults'
import { isAllowedExternalUrl } from '../../shared/ipc'
import { APP_VERSION, type AppConfig, type SavedCrosshairPreset } from '../../shared/types'
import {
  activeProfile,
  configPath,
  loadConfig,
  saveConfig,
  updateActiveProfile,
  updateConfig
} from '../services/config'
import { requestQuit } from '../quit'
import { detectFortniteInstall, persistFortnitePath, validateFortnitePath, getLaunchStatus } from '../services/fortnite'
import { fireScrimAlert, refreshScrims, updateScrims } from '../services/scrims'
import { detectLastUsedSkin, importSkinPreview } from '../services/skin'
import { clearAvatar, importAvatar, readAvatarDataUrl } from '../services/avatar'
import { checkForUpdates, openReleasesPage } from '../services/updates'
import { FORTNITE_DIALOG_FILTERS, fortniteBrowseStartDir } from '../services/windows-api'
import { launchFromActiveProfile } from '../services/launcher'
import { startMacro, stopMacro, updateMacroRuntime } from '../services/macro'
import { snapshot } from '../services/monitor'
import { applyCleanup, listCandidates, restoreCleanup } from '../services/performance'
import {
  applyResolution,
  backupFortniteConfig,
  getDisplayInfo,
  gpuGuidance,
  restoreNativeDisplay
} from '../services/resolution'
import { startOverlay, stopOverlay, updateOverlay } from '../services/overlay'
import { applyLoginItem, syncHardwareAccelerationNote } from '../services/settings-os'
import {
  cleanupSchema,
  configPatchSchema,
  crosshairSchema,
  idSchema,
  macroSchema,
  nameSchema,
  pathSchema,
  profilePatchSchema,
  resolutionApplySchema,
  savePresetSchema,
  testResolutionSchema,
  urlSchema
} from './schemas'

function sendToast(win: BrowserWindow | null, tone: 'info' | 'success' | 'warn' | 'error', title: string, body?: string) {
  win?.webContents.send('toast:show', { id: createId('toast'), tone, title, body })
}

function broadcastConfig(win: BrowserWindow | null): AppConfig {
  const config = loadConfig()
  win?.webContents.send('config:changed', config)
  return config
}

export function registerIpc(getWindow: () => BrowserWindow | null): void {
  ipcMain.handle('config:get', () => loadConfig())

  ipcMain.handle('config:update', (_event, raw) => {
    const patch = configPatchSchema.parse(raw)
    const next = updateConfig(patch)
    if (patch.general) applyLoginItem(next.general)
    return next
  })

  ipcMain.handle('profiles:create', (_event, raw) => {
    const { name } = nameSchema.parse(raw)
    const config = loadConfig()
    const profile = createProfile(name)
    const next = saveConfig({ ...config, profiles: [...config.profiles, profile], activeProfileId: profile.id })
    return next
  })

  ipcMain.handle('profiles:update', (_event, raw) => {
    const parsed = z.object({ id: idSchema.shape.id }).and(profilePatchSchema).parse(raw)
    const config = loadConfig()
    const profiles = config.profiles.map((profile) =>
      profile.id === parsed.id
        ? {
            ...profile,
            name: parsed.name ?? profile.name,
            crosshair: { ...profile.crosshair, ...parsed.crosshair },
            resolution: { ...profile.resolution, ...parsed.resolution },
            graphics: { ...profile.graphics, ...parsed.graphics },
            performance: { ...profile.performance, ...parsed.performance },
            macro: { ...profile.macro, ...parsed.macro },
            updatedAt: new Date().toISOString()
          }
        : profile
    )
    return saveConfig({ ...config, profiles })
  })

  ipcMain.handle('profiles:delete', (_event, raw) => {
    const { id } = idSchema.parse(raw)
    const config = loadConfig()
    if (config.profiles.length <= 1) {
      return { ok: false, message: 'Keep at least one profile.' }
    }
    const profiles = config.profiles.filter((p) => p.id !== id)
    const activeProfileId = config.activeProfileId === id ? profiles[0].id : config.activeProfileId
    const defaultProfileId = config.defaultProfileId === id ? profiles[0].id : config.defaultProfileId
    return { ok: true, message: 'Profile deleted.', data: saveConfig({ ...config, profiles, activeProfileId, defaultProfileId }) }
  })

  ipcMain.handle('profiles:duplicate', (_event, raw) => {
    const { id } = idSchema.parse(raw)
    const config = loadConfig()
    const source = config.profiles.find((p) => p.id === id)
    if (!source) return loadConfig()
    const copy = {
      ...structuredClone(source),
      id: createId('profile'),
      name: `${source.name} Copy`,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }
    return saveConfig({ ...config, profiles: [...config.profiles, copy], activeProfileId: copy.id })
  })

  ipcMain.handle('profiles:set-active', (_event, raw) => {
    const { id } = idSchema.parse(raw)
    return updateConfig({ activeProfileId: id })
  })

  ipcMain.handle('profiles:set-default', (_event, raw) => {
    const { id } = idSchema.parse(raw)
    return updateConfig({ defaultProfileId: id })
  })

  ipcMain.handle('fortnite:detect', () => detectFortniteInstall())
  ipcMain.handle('fortnite:validate', (_event, raw) => validateFortnitePath(pathSchema.parse(raw).path))
  ipcMain.handle('fortnite:set-path', (_event, raw) => persistFortnitePath(pathSchema.parse(raw).path))
  ipcMain.handle('fortnite:browse', async () => {
    const win = getWindow()
    const options: OpenDialogOptions = {
      title: 'Select FortniteClient-Win64-Shipping.exe, FortniteBootstrapper.exe, or Fortnite.exe',
      properties: ['openFile'],
      filters: FORTNITE_DIALOG_FILTERS,
      defaultPath: fortniteBrowseStartDir()
    }
    const result = await (win ? dialog.showOpenDialog(win, options) : dialog.showOpenDialog(options))
    if (result.canceled || !result.filePaths[0]) {
      return { found: false, path: null, version: null, valid: false, source: 'browse', reason: 'Browse cancelled.' }
    }
    return persistFortnitePath(result.filePaths[0])
  })
  ipcMain.handle('fortnite:launch', async () => {
    const win = getWindow()
    const result = await launchFromActiveProfile()
    sendToast(win, result.ok ? (result.code ? 'warn' : 'success') : 'error', result.ok ? 'Launch' : 'Launch blocked', result.message)
    return result
  })
  ipcMain.handle('fortnite:status', () => getLaunchStatus())

  ipcMain.handle('resolution:info', () => getDisplayInfo())
  ipcMain.handle('resolution:gpu', () => gpuGuidance())
  ipcMain.handle('resolution:apply', async (_event, raw) => {
    const { settings, permanent } = resolutionApplySchema.parse(raw)
    updateActiveProfile((profile) => ({ ...profile, resolution: settings }))
    const result = await applyResolution(settings, permanent)
    sendToast(getWindow(), result.ok ? 'success' : 'error', result.ok ? 'Resolution' : 'Resolution failed', result.message)
    return result
  })
  ipcMain.handle('resolution:restore-native', async () => {
    const result = await restoreNativeDisplay()
    sendToast(getWindow(), result.ok ? 'success' : 'error', 'Restore native', result.message)
    return result
  })
  ipcMain.handle('resolution:test', async (_event, raw) => {
    const { width, height } = testResolutionSchema.parse(raw)
    const config = loadConfig()
    const profile = getActiveProfile(config)
    const result = await applyResolution({ ...profile.resolution, width, height, temporary: true }, false)
    sendToast(getWindow(), result.ok ? 'info' : 'error', 'Test resolution', result.ok ? 'Applied for this session. Use Restore Native to revert immediately.' : result.message)
    return result
  })
  ipcMain.handle('resolution:backup-config', () => backupFortniteConfig())

  ipcMain.handle('crosshair:start', async () => {
    try {
      await startOverlay(activeProfile().crosshair)
      return { ok: true, message: 'Overlay started.' }
    } catch (error) {
      return { ok: false, code: 'OVERLAY_FAILED', message: error instanceof Error ? error.message : 'Overlay failed.' }
    }
  })
  ipcMain.handle('crosshair:stop', () => {
    stopOverlay()
    return { ok: true, message: 'Overlay stopped.' }
  })
  ipcMain.handle('crosshair:update', async (_event, raw) => {
    const settings = crosshairSchema.parse(raw)
    const next = updateActiveProfile((profile) => ({ ...profile, crosshair: settings }))
    updateOverlay(settings)
    if (settings.enabled && !settings.onlyWhileFortnite) {
      await startOverlay(settings)
    }
    if (!settings.enabled) stopOverlay()
    return next
  })
  ipcMain.handle('crosshair:import-image', async () => {
    const win = getWindow()
    const result = await (win
      ? dialog.showOpenDialog(win, {
          title: 'Import crosshair image',
          properties: ['openFile'],
          filters: [{ name: 'Images', extensions: ['png', 'svg'] }]
        })
      : dialog.showOpenDialog({
          title: 'Import crosshair image',
          properties: ['openFile'],
          filters: [{ name: 'Images', extensions: ['png', 'svg'] }]
        }))
    if (result.canceled || !result.filePaths[0]) {
      return { ok: false, message: 'Import cancelled.' }
    }
    const filePath = result.filePaths[0]
    const ext = path.extname(filePath).toLowerCase()
    if (ext !== '.png' && ext !== '.svg') {
      return { ok: false, message: 'Only PNG or SVG images are allowed.' }
    }
    const buffer = readFileSync(filePath)
    if (buffer.byteLength > 1_500_000) {
      return { ok: false, message: 'Image is larger than 1.5 MB.' }
    }
    const mime = ext === '.svg' ? 'image/svg+xml' : 'image/png'
    const dataUrl = `data:${mime};base64,${buffer.toString('base64')}`
    const next = updateActiveProfile((profile) => ({
      ...profile,
      crosshair: {
        ...profile.crosshair,
        shape: 'custom-image',
        customImage: dataUrl,
        customImageName: path.basename(filePath)
      }
    }))
    updateOverlay(getActiveProfile(next).crosshair)
    return { ok: true, message: 'Image imported.', data: next }
  })
  ipcMain.handle('crosshair:save-preset', (_event, raw) => {
    const { name } = savePresetSchema.parse(raw)
    const config = loadConfig()
    const profile = getActiveProfile(config)
    const preset: SavedCrosshairPreset = {
      id: createId('xh'),
      name,
      settings: {
        presetId: createId('custom'),
        color: profile.crosshair.color,
        opacity: profile.crosshair.opacity,
        size: profile.crosshair.size,
        thickness: profile.crosshair.thickness,
        gap: profile.crosshair.gap,
        outline: profile.crosshair.outline,
        outlineThickness: profile.crosshair.outlineThickness,
        outlineColor: profile.crosshair.outlineColor,
        showDot: profile.crosshair.showDot,
        dotSize: profile.crosshair.dotSize,
        horizontal: profile.crosshair.horizontal,
        vertical: profile.crosshair.vertical,
        rotation: profile.crosshair.rotation,
        shape: profile.crosshair.shape,
        centerGap: profile.crosshair.centerGap
      }
    }
    return saveConfig({ ...config, savedCrosshairPresets: [...config.savedCrosshairPresets, preset] })
  })
  ipcMain.handle('crosshair:delete-preset', (_event, raw) => {
    const { id } = idSchema.parse(raw)
    const config = loadConfig()
    return saveConfig({
      ...config,
      savedCrosshairPresets: config.savedCrosshairPresets.filter((p) => p.id !== id)
    })
  })

  ipcMain.handle('performance:candidates', () => listCandidates())
  ipcMain.handle('performance:cleanup', async (_event, raw) => applyCleanup(cleanupSchema.parse(raw).ids))
  ipcMain.handle('performance:restore', () => restoreCleanup())

  ipcMain.handle('macro:start', () => startMacro(activeProfile().macro))
  ipcMain.handle('macro:stop', () => stopMacro())
  ipcMain.handle('macro:update', (_event, raw) => {
    const settings = macroSchema.parse(raw)
    const next = updateActiveProfile((profile) => ({ ...profile, macro: settings }))
    updateMacroRuntime(settings)
    return next
  })

  ipcMain.handle('monitor:snapshot', () => snapshot())
  ipcMain.handle('window:minimize', () => {
    getWindow()?.minimize()
  })
  ipcMain.handle('window:maximize', () => {
    const win = getWindow()
    if (!win) return
    if (win.isMaximized()) win.unmaximize()
    else win.maximize()
  })
  ipcMain.handle('window:close', () => {
    requestQuit()
  })
  ipcMain.handle('window:open-external', async (_event, raw) => {
    const { url } = urlSchema.parse(raw)
    if (!isAllowedExternalUrl(url)) {
      return { ok: false, message: 'Only Discord HTTPS links can be opened from Avix.' }
    }
    await shell.openExternal(url)
    return { ok: true, message: 'Opened Discord.' }
  })
  ipcMain.handle('skin:detect', () => detectLastUsedSkin())
  ipcMain.handle('skin:import', () => importSkinPreview(getWindow()))
  ipcMain.handle('avatar:get', () => readAvatarDataUrl())
  ipcMain.handle('avatar:import', () => importAvatar(getWindow()))
  ipcMain.handle('avatar:clear', () => {
    const avatar = clearAvatar()
    return { ok: true, message: 'Profile picture removed.', data: avatar }
  })
  ipcMain.handle('app:info', () => ({
    configPath: configPath(),
    userData: path.dirname(configPath()),
    version: APP_VERSION
  }))
  ipcMain.handle('updates:open', async (_event, raw) => {
    const parsed = z.object({ url: z.string().url().optional() }).parse(raw ?? {})
    await openReleasesPage(parsed.url)
    return { ok: true, message: 'Opened the download page.' }
  })
  ipcMain.handle('skin:update', (_event, raw) => {
    const parsed = z.object({
      name: z.string().max(64).nullable(),
      image: z.string().nullable(),
      source: z.enum(['log', 'upload', 'placeholder']),
      cosmeticId: z.string().max(80).nullable()
    }).parse(raw)
    return updateConfig({ skin: parsed })
  })
  ipcMain.handle('scrims:list', () => loadConfig().scrims)
  ipcMain.handle('scrims:update', (_event, raw) => {
    const parsed = z.object({
      pollSeconds: z.number().min(20).max(600),
      sources: z.array(z.object({
        id: z.string().min(1).max(80),
        name: z.string().min(1).max(48),
        enabled: z.boolean(),
        statusUrl: z.string().nullable(),
        lastLiveAt: z.string().nullable()
      })).max(24)
    }).parse(raw)
    return updateScrims(parsed)
  })
  ipcMain.handle('scrims:refresh', () => refreshScrims())
  ipcMain.handle('scrims:test', async (_event, raw) => {
    const { id } = idSchema.parse(raw)
    const source = loadConfig().scrims.sources.find((item) => item.id === id)
    if (!source) return { ok: false, message: 'Unknown scrim source.' }
    await fireScrimAlert(source)
    return { ok: true, message: `Alert fired for ${source.name}.` }
  })

  ipcMain.handle('settings:apply-general', (_event, raw) => {
    const parsed = configPatchSchema.parse({ general: raw })
    const next = updateConfig(parsed)
    applyLoginItem(next.general)
    syncHardwareAccelerationNote(next.general.hardwareAcceleration)
    return next
  })

  ipcMain.handle('wizard:complete', () => updateConfig({ wizardCompleted: true }))
  ipcMain.handle('updates:check', () => checkForUpdates())
}
