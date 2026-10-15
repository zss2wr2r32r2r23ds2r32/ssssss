import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { screen } from 'electron'
import type { DisplayInfo, GpuInfo, GpuVendor, OperationResult, ResolutionSettings } from '../../shared/types'
import { backupsDir, loadConfig, updateConfig } from './config'
import { fortniteConfigPath, isWindows, runPowerShell } from './windows-api'
import {
  displayChange,
  displayCurrentMode,
  displayDeviceAt,
  displayHelperPresent,
  displayListModes,
  displayRestore,
  nvidiaProbe
} from './windows-display'
import { recenterOverlay } from './overlay'

interface RestoreState {
  display: { width: number; height: number; device: string } | null
  gameUserSettings: string | null
  appliedTemporary: boolean
}

const restoreState: RestoreState = {
  display: null,
  gameUserSettings: null,
  appliedTemporary: false
}

function uniqueModes(modes: Array<{ width: number; height: number }>): Array<{ width: number; height: number }> {
  const seen = new Set<string>()
  const out: Array<{ width: number; height: number }> = []
  for (const mode of modes) {
    const key = `${mode.width}x${mode.height}`
    if (seen.has(key)) continue
    seen.add(key)
    out.push({ width: mode.width, height: mode.height })
  }
  return out
}

export async function getDisplayInfo(): Promise<DisplayInfo[]> {
  let enumerated: Array<{ width: number; height: number }> = []
  if (isWindows) {
    try {
      enumerated = (await displayListModes('')).map((mode) => ({ width: mode.width, height: mode.height }))
    } catch {
      enumerated = []
    }
  }
  return screen.getAllDisplays().map((display, index) => ({
    id: display.id,
    label: display.label || `Display ${index + 1}`,
    bounds: display.bounds,
    workArea: display.workArea,
    scaleFactor: display.scaleFactor,
    primary: display.id === screen.getPrimaryDisplay().id,
    currentWidth: display.size.width,
    currentHeight: display.size.height,
    availableModes: uniqueModes([
      { width: display.size.width, height: display.size.height },
      ...enumerated,
      ...[1920, 1728, 1720, 1600, 1620, 1500, 1440, 1280].map((width) => ({ width, height: 1080 }))
    ])
  }))
}

export function rememberNativeIfNeeded(): void {
  const config = loadConfig()
  if (config.lastNativeResolution) return
  const primary = screen.getPrimaryDisplay()
  updateConfig({
    lastNativeResolution: {
      width: primary.size.width,
      height: primary.size.height,
      savedAt: new Date().toISOString()
    }
  })
}

let cachedGpu: GpuInfo | null = null
let cachedGpuAt = 0

export async function detectGpu(): Promise<GpuInfo> {
  if (cachedGpu && Date.now() - cachedGpuAt < 10 * 60 * 1000) return cachedGpu
  if (!isWindows) {
    cachedGpu = {
      vendor: 'Unknown',
      name: `${process.platform} (GPU detection is Windows-only)`,
      details: ['Run Avix on Windows to read the installed graphics adapter.']
    }
    cachedGpuAt = Date.now()
    return cachedGpu
  }
  try {
    const script = `
      Get-CimInstance Win32_VideoController | Select-Object Name, AdapterRAM, DriverVersion | ConvertTo-Json -Compress
    `
    const raw = await runPowerShell(script)
    const parsed = JSON.parse(raw) as Array<{ Name?: string; DriverVersion?: string }> | { Name?: string; DriverVersion?: string }
    const items = Array.isArray(parsed) ? parsed : [parsed]
    const name = items.map((i) => i.Name).filter(Boolean).join(' + ') || 'Unknown GPU'
    const vendor: GpuVendor = /nvidia/i.test(name)
      ? 'NVIDIA'
      : /amd|radeon/i.test(name)
        ? 'AMD'
        : /intel/i.test(name)
          ? 'Intel'
          : 'Unknown'
    cachedGpu = {
      vendor,
      name,
      details: items.map((i) => `${i.Name ?? 'GPU'}${i.DriverVersion ? ` · driver ${i.DriverVersion}` : ''}`)
    }
  } catch {
    cachedGpu = { vendor: 'Unknown', name: 'Unable to query GPU', details: [] }
  }
  cachedGpuAt = Date.now()
  return cachedGpu
}

function scalingCopy(vendor: GpuVendor): string[] {
  const common = [
    'Avix changes the Windows display mode for this Fortnite session only. It does not attach to the Fortnite process.',
    'Native mode is saved first and restored when Fortnite exits, crashes, or you click Restore Native.',
    'If the size is not in the NVIDIA/Windows mode list, create that custom resolution once in the GPU control panel, then Avix can select it.',
    'GPU scaling stretches the desktop to the monitor. That is a driver setting, not Fortnite FOV.',
    'Epic competitive play uses 16:9. Stretched desktop resolutions are a display-scale choice, not a claimed advantage.'
  ]
  if (vendor === 'NVIDIA') {
    return [
      ...common,
      'NVIDIA path: Avix selects a mode Windows/NVIDIA already exposes, then applies it with ChangeDisplaySettingsEx (CDS_FULLSCREEN) for this session.',
      'Create a missing size once: NVIDIA Control Panel → Change resolution → Customize → Create Custom Resolution. Then set Adjust desktop size and position → Perform scaling on: GPU, Scaling mode: Full-screen.',
      'Avix probes nvapi64.dll to confirm the NVIDIA driver is present. It does not inject into Fortnite or write Epic credentials.'
    ]
  }
  if (vendor === 'AMD') {
    return [
      ...common,
      'AMD: Radeon Software → Display → Scaling Mode → Full panel. GPU scaling stretches in the driver, not inside Fortnite.'
    ]
  }
  if (vendor === 'Intel') {
    return [
      ...common,
      'Intel: Graphics Command Center → Display → Scale → Full screen. This is a display option, not a Fortnite setting.'
    ]
  }
  return common
}

export async function gpuGuidance(): Promise<{ gpu: GpuInfo; guidance: string[] }> {
  const gpu = await detectGpu()
  return { gpu, guidance: scalingCopy(gpu.vendor) }
}

function upsertIni(content: string, key: string, value: string): string {
  const pattern = new RegExp(`^${key}=.*$`, 'im')
  if (pattern.test(content)) return content.replace(pattern, `${key}=${value}`)
  if (/\[\/Script\/FortniteGame\.FortGameUserSettings\]/i.test(content)) {
    return content.replace(
      /\[\/Script\/FortniteGame\.FortGameUserSettings\]/i,
      `[/Script/FortniteGame.FortGameUserSettings]\n${key}=${value}`
    )
  }
  return `${content.trim()}\n[/Script/FortniteGame.FortGameUserSettings]\n${key}=${value}\n`
}

export function backupFortniteConfig(): OperationResult<{ backupPath: string | null }> {
  const source = fortniteConfigPath()
  if (!source || !existsSync(source)) {
    return { ok: false, message: 'Fortnite GameUserSettings.ini was not found. Launch Fortnite once to generate it.', data: { backupPath: null } }
  }
  const dest = path.join(backupsDir(), `GameUserSettings-${Date.now()}.ini`)
  mkdirSync(path.dirname(dest), { recursive: true })
  copyFileSync(source, dest)
  if (!restoreState.gameUserSettings) {
    restoreState.gameUserSettings = readFileSync(source, 'utf8')
  }
  return { ok: true, message: 'Fortnite config backed up.', data: { backupPath: dest } }
}

export function applyFortniteConfig(settings: ResolutionSettings, graphics: {
  fullscreen: boolean
  vsync: boolean
  performanceMode: boolean
  lowGraphics: boolean
  fpsLimit: number | 'unlimited'
}, permanent: boolean): OperationResult {
  const target = fortniteConfigPath()
  if (!target) {
    return { ok: false, code: 'APPLY_FAILED', message: 'Could not resolve Fortnite config path.' }
  }
  if (!existsSync(target)) {
    return {
      ok: false,
      code: 'APPLY_FAILED',
      message: 'GameUserSettings.ini is missing. Launch Fortnite once so Epic can create it, then try again.'
    }
  }
  backupFortniteConfig()
  let content = readFileSync(target, 'utf8')
  if (settings.applyGameUserSettings) {
    content = upsertIni(content, 'ResolutionSizeX', String(settings.width))
    content = upsertIni(content, 'ResolutionSizeY', String(settings.height))
    content = upsertIni(content, 'LastUserConfirmedResolutionSizeX', String(settings.width))
    content = upsertIni(content, 'LastUserConfirmedResolutionSizeY', String(settings.height))
    content = upsertIni(content, 'FullscreenMode', '0')
    content = upsertIni(content, 'PreferredFullscreenMode', '0')
  }
  content = upsertIni(content, 'bUseVSync', graphics.vsync ? 'True' : 'False')
  content = upsertIni(content, 'FrameRateLimit', graphics.fpsLimit === 'unlimited' ? '0.000000' : `${graphics.fpsLimit}.000000`)
  content = upsertIni(content, 'bUseDesktopResolutionForFullscreen', 'False')
  if (graphics.performanceMode) {
    content = upsertIni(content, 'MobileFPSMode', 'Mode_60Fps')
  }
  writeFileSync(target, content, 'utf8')
  restoreState.appliedTemporary = !permanent && settings.temporary
  return { ok: true, message: permanent ? 'Fortnite config saved.' : 'Temporary Fortnite config applied. It will restore when Fortnite closes.' }
}

function gamingPhysicalPoint(): { x: number; y: number } {
  try {
    const dip = screen.getCursorScreenPoint()
    return screen.dipToScreenPoint(dip)
  } catch {
    const primary = screen.getPrimaryDisplay()
    return {
      x: Math.round(primary.bounds.x + primary.bounds.width / 2),
      y: Math.round(primary.bounds.y + primary.bounds.height / 2)
    }
  }
}

async function resolveGamingDevice(): Promise<string> {
  if (!isWindows) return ''
  try {
    const point = gamingPhysicalPoint()
    return (await displayDeviceAt(point.x, point.y)).trim()
  } catch {
    return ''
  }
}

async function changeDisplay(width: number, height: number): Promise<OperationResult> {
  if (!isWindows) {
    return { ok: false, code: 'APPLY_FAILED', message: 'Display mode changes require Windows.' }
  }
  rememberNativeIfNeeded()
  const device = await resolveGamingDevice()
  const current = await displayCurrentMode(device).catch(() => null)
  if (!restoreState.display && current) {
    restoreState.display = { width: current.width, height: current.height, device }
  } else if (!restoreState.display) {
    const primary = screen.getPrimaryDisplay()
    restoreState.display = { width: primary.size.width, height: primary.size.height, device }
  }
  if (!loadConfig().lastNativeResolution && restoreState.display) {
    updateConfig({
      lastNativeResolution: {
        width: restoreState.display.width,
        height: restoreState.display.height,
        savedAt: new Date().toISOString()
      }
    })
  }

  const modes = await displayListModes(device).catch(() => [])
  const matches = modes.filter((mode) => mode.width === width && mode.height === height)
  const listed = matches.length > 0
  const freq = listed ? Math.max(...matches.map((mode) => mode.freq)) : 0
  const gpu = await detectGpu()
  const nvidia = gpu.vendor === 'NVIDIA' ? await nvidiaProbe().catch(() => 'MISSING') : 'SKIP'

  if (!displayHelperPresent()) {
    return {
      ok: false,
      code: 'APPLY_FAILED',
      message:
        'The Avix display helper is missing from this install. Reinstall Avix (portable or NSIS). Fortnite can still be launched from Home.'
    }
  }

  try {
    const result = await displayChange(device, width, height, freq)
    if (result === 'OK' || result.startsWith('OK')) {
      restoreState.appliedTemporary = true
      void recenterOverlay()
      const nvidiaNote =
        gpu.vendor === 'NVIDIA'
          ? nvidia === 'OK'
            ? ' NVIDIA driver responded (nvapi64). Keep GPU / Full-screen scaling in NVIDIA Control Panel.'
            : ' NVIDIA custom modes must already exist in NVIDIA Control Panel before Avix can select them.'
          : ''
      return {
        ok: true,
        message: listed
          ? `Display set to ${width}×${height}${freq ? ` @ ${freq}Hz` : ''} on the gaming monitor.${nvidiaNote} Native mode restores when Fortnite closes.`
          : `Windows accepted ${width}×${height} even though it was not listed.${nvidiaNote} Native mode restores when Fortnite closes.`
      }
    }
    return {
      ok: false,
      code: 'UNSUPPORTED_RES',
      message: listed
        ? `Windows rejected ${width}×${height} (${result}).`
        : gpu.vendor === 'NVIDIA'
          ? `${width}×${height} is not in the NVIDIA/Windows mode list (${result}). NVIDIA Control Panel → Change resolution → Customize → Create Custom Resolution, then Apply again. Also set scaling to GPU / Full-screen.`
          : `Windows does not list ${width}×${height} (${result}). Add that custom mode in your GPU control panel, then apply again.`
    }
  } catch (error) {
    return { ok: false, code: 'APPLY_FAILED', message: error instanceof Error ? error.message : 'Display change failed.' }
  }
}

export async function applyResolution(settings: ResolutionSettings, permanent: boolean): Promise<OperationResult> {
  rememberNativeIfNeeded()
  if (settings.width < 640 || settings.height < 480 || settings.width > 7680 || settings.height > 4320) {
    return { ok: false, code: 'UNSUPPORTED_RES', message: 'Resolution is outside a safe range.' }
  }
  const messages: string[] = []
  if (settings.method !== 'fortnite-only') {
    const displayResult = await changeDisplay(settings.width, settings.height)
    if (!displayResult.ok) return displayResult
    messages.push(displayResult.message)
  }
  if (settings.applyGameUserSettings) {
    const config = loadConfig()
    const profile = config.profiles.find((p) => p.id === config.activeProfileId)
    if (profile) {
      const ini = applyFortniteConfig(settings, profile.graphics, permanent)
      if (!ini.ok && settings.method === 'fortnite-only') return ini
      if (ini.message) messages.push(ini.message)
    }
  }
  void recenterOverlay()
  return { ok: true, message: messages.join(' ') || 'Resolution preference applied.' }
}

export async function restoreNativeDisplay(): Promise<OperationResult> {
  const config = loadConfig()
  const native = config.lastNativeResolution
  const saved = restoreState.display
  const device = saved?.device ?? ''
  const messages: string[] = []
  if (restoreState.gameUserSettings) {
    const target = fortniteConfigPath()
    if (target && existsSync(target)) {
      writeFileSync(target, restoreState.gameUserSettings, 'utf8')
      messages.push('Restored Fortnite GameUserSettings backup.')
    }
    restoreState.gameUserSettings = null
  }
  if (isWindows) {
    try {
      const restored = await displayRestore(device)
      if (restored === 'OK' || restored.startsWith('OK')) {
        messages.push('Restored the native Windows display mode.')
      } else {
        const width = native?.width ?? saved?.width
        const height = native?.height ?? saved?.height
        if (width && height) {
          const fallback = await displayChange(device, width, height, 0)
          messages.push(
            fallback === 'OK' || fallback.startsWith('OK')
              ? `Restored ${width}×${height}.`
              : `Display restore returned ${restored} / ${fallback}.`
          )
        } else {
          messages.push(`Display restore returned ${restored}.`)
        }
      }
    } catch (error) {
      return {
        ok: false,
        code: 'APPLY_FAILED',
        message: error instanceof Error ? error.message : 'Display restore failed.'
      }
    }
  } else {
    messages.push('Display restore is a no-op outside Windows.')
  }
  restoreState.display = null
  restoreState.appliedTemporary = false
  void recenterOverlay()
  return { ok: true, message: messages.join(' ') || 'Nothing to restore.' }
}

export async function restoreTemporaryIfNeeded(): Promise<void> {
  if (restoreState.appliedTemporary) {
    await restoreNativeDisplay()
  }
}

export function hasTemporaryChanges(): boolean {
  return restoreState.appliedTemporary
}
