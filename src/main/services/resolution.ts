import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { screen } from 'electron'
import type { DisplayInfo, GpuInfo, GpuVendor, OperationResult, ResolutionSettings } from '../../shared/types'
import { backupsDir, loadConfig, updateConfig } from './config'
import { fortniteConfigPath, isWindows, runPowerShell } from './windows-api'

interface RestoreState {
  display: { width: number; height: number } | null
  gameUserSettings: string | null
  appliedTemporary: boolean
}

const restoreState: RestoreState = {
  display: null,
  gameUserSettings: null,
  appliedTemporary: false
}

export function getDisplayInfo(): DisplayInfo[] {
  return screen.getAllDisplays().map((display, index) => ({
    id: display.id,
    label: display.label || `Display ${index + 1}`,
    bounds: display.bounds,
    workArea: display.workArea,
    scaleFactor: display.scaleFactor,
    primary: display.id === screen.getPrimaryDisplay().id,
    currentWidth: display.size.width,
    currentHeight: display.size.height,
    availableModes: (display.displayFrequency ? [{ width: display.size.width, height: display.size.height }] : [
      { width: display.size.width, height: display.size.height }
    ]).concat(
      [1920, 1728, 1600, 1620, 1500, 1440, 1280]
        .map((width) => ({ width, height: 1080 }))
        .filter((mode) => mode.width !== display.size.width || mode.height !== display.size.height)
    )
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

export async function detectGpu(): Promise<GpuInfo> {
  if (!isWindows) {
    return {
      vendor: 'Unknown',
      name: `${process.platform} (GPU detection is Windows-only)`,
      details: ['Run Avix on Windows to read the installed graphics adapter.']
    }
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
    return {
      vendor,
      name,
      details: items.map((i) => `${i.Name ?? 'GPU'}${i.DriverVersion ? ` · driver ${i.DriverVersion}` : ''}`)
    }
  } catch {
    return { vendor: 'Unknown', name: 'Unable to query GPU', details: [] }
  }
}

function scalingCopy(vendor: GpuVendor): string[] {
  const common = [
    'Display scaling changes how the desktop is stretched to the monitor. It does not change Fortnite FOV by itself.',
    'Epic competitive play uses 16:9. Stretched desktop resolutions are a display-scale choice, not a claimed advantage.',
    'Prefer Fortnite-only GameUserSettings changes when you want temporary, game-scoped handling.'
  ]
  if (vendor === 'NVIDIA') {
    return [
      ...common,
      'NVIDIA: Control Panel → Display → Adjust desktop size and position. GPU scaling stretches in the GPU; Display scaling uses the monitor.'
    ]
  }
  if (vendor === 'AMD') {
    return [
      ...common,
      'AMD: Radeon Software → Display → Scaling Mode. GPU vs Display vs Preserve Aspect Ratio are driver settings, not Fortnite FOV.'
    ]
  }
  if (vendor === 'Intel') {
    return [
      ...common,
      'Intel: Graphics Command Center → Display → Scale. Full-screen scale is a display option, not a Fortnite setting.'
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
    content = upsertIni(content, 'FullscreenMode', settings.method === 'fortnite-only' ? '0' : '0')
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

async function changeDisplay(width: number, height: number): Promise<OperationResult> {
  if (!isWindows) {
    return { ok: false, code: 'APPLY_FAILED', message: 'Display mode changes require Windows.' }
  }
  rememberNativeIfNeeded()
  const primary = screen.getPrimaryDisplay()
  if (!restoreState.display) {
    restoreState.display = { width: primary.size.width, height: primary.size.height }
  }
  const script = `
    Add-Type @"
      using System;
      using System.Runtime.InteropServices;
      public class NauticalDisplay {
        [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Ansi)]
        public struct DEVMODE {
          [MarshalAs(UnmanagedType.ByValTStr, SizeConst=32)] public string dmDeviceName;
          public short dmSpecVersion, dmDriverVersion, dmSize, dmDriverExtra;
          public int dmFields, dmPositionX, dmPositionY, dmDisplayOrientation, dmDisplayFixedOutput;
          public short dmColor, dmDuplex, dmYResolution, dmTTOption, dmCollate;
          [MarshalAs(UnmanagedType.ByValTStr, SizeConst=32)] public string dmFormName;
          public short dmLogPixels;
          public int dmBitsPerPel, dmPelsWidth, dmPelsHeight, dmDisplayFlags, dmDisplayFrequency, dmICMMethod, dmICMIntent, dmMediaType, dmDitherType, dmReserved1, dmReserved2, dmPanningWidth, dmPanningHeight;
        }
        [DllImport("user32.dll")] public static extern int ChangeDisplaySettings(ref DEVMODE devMode, int flags);
        [DllImport("user32.dll")] public static extern int EnumDisplaySettings(string deviceName, int modeNum, ref DEVMODE devMode);
      }
"@
      $mode = New-Object NauticalDisplay+DEVMODE
      $mode.dmSize = [System.Runtime.InteropServices.Marshal]::SizeOf($mode)
      [void][NauticalDisplay]::EnumDisplaySettings($null, -1, [ref]$mode)
      $mode.dmPelsWidth = ${width}
      $mode.dmPelsHeight = ${height}
      $mode.dmFields = 0x80000 -bor 0x100000
      $result = [NauticalDisplay]::ChangeDisplaySettings([ref]$mode, 0)
      $result
  `
  try {
    const result = await runPowerShell(script)
    if (result.trim() === '0') {
      restoreState.appliedTemporary = true
      return { ok: true, message: `Display set to ${width}×${height}. Native mode will restore when Fortnite closes unless you save permanently.` }
    }
    return {
      ok: false,
      code: 'UNSUPPORTED_RES',
      message: `Windows rejected ${width}×${height} (ChangeDisplaySettings ${result}). The mode may be unsupported on this display.`
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
  if (settings.method === 'display' || settings.method === 'gpu' || settings.method === 'automatic') {
    const displayResult = await changeDisplay(settings.width, settings.height)
    if (!displayResult.ok) return displayResult
  }
  if (settings.applyGameUserSettings || settings.method === 'fortnite-only') {
    const config = loadConfig()
    const profile = config.profiles.find((p) => p.id === config.activeProfileId)
    if (profile) {
      return applyFortniteConfig(settings, profile.graphics, permanent)
    }
  }
  return { ok: true, message: 'Resolution preference applied.' }
}

export async function restoreNativeDisplay(): Promise<OperationResult> {
  const config = loadConfig()
  const native = config.lastNativeResolution ?? restoreState.display
  const messages: string[] = []
  if (restoreState.gameUserSettings) {
    const target = fortniteConfigPath()
    if (target && existsSync(target)) {
      writeFileSync(target, restoreState.gameUserSettings, 'utf8')
      messages.push('Restored Fortnite GameUserSettings backup.')
    }
    restoreState.gameUserSettings = null
  }
  if (native && isWindows) {
    const result = await changeDisplay(native.width, native.height)
    messages.push(result.message)
    if (!result.ok) {
      return { ok: false, code: result.code, message: messages.join(' ') }
    }
  } else if (!isWindows) {
    messages.push('Display restore is a no-op outside Windows.')
  }
  restoreState.display = null
  restoreState.appliedTemporary = false
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
