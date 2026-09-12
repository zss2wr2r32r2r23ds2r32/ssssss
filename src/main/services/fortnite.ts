import { spawn } from 'node:child_process'
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import {
  collectHintsFromManifestText,
  executablesForInstall,
  rankFortniteCandidates
} from '../../shared/fortnite-detect'
import type { FortniteInstallInfo, LaunchStatus, StatusEvent } from '../../shared/types'
import { loadConfig, updateConfig } from './config'
import {
  commonFortniteCandidates,
  epicManifestDirs,
  FORTNITE_PROCESS_NAMES,
  isWindows,
  launcherInstalledPath,
  looksLikeFortniteExecutable,
  runPowerShell
} from './windows-api'

type Listener = (event: StatusEvent) => void

let status: LaunchStatus = 'NOT_RUNNING'
let trackedPid: number | null = null
let startedAt: number | null = null
let pollTimer: NodeJS.Timeout | null = null
const listeners = new Set<Listener>()

export function getLaunchStatus(): LaunchStatus {
  return status
}

export function getTrackedPid(): number | null {
  return trackedPid
}

export function getUptimeSec(): number | null {
  if (!startedAt) return null
  return Math.max(0, Math.round((Date.now() - startedAt) / 1000))
}

export function onStatus(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function emit(event: StatusEvent): void {
  for (const listener of listeners) listener(event)
}

export function setStatus(next: LaunchStatus, extra: Partial<StatusEvent> = {}): void {
  status = next
  if (next === 'NOT_RUNNING' || next === 'CLOSED') {
    if (next === 'NOT_RUNNING') {
      trackedPid = null
      startedAt = null
    }
  }
  emit({
    status,
    pid: trackedPid,
    unexpected: extra.unexpected ?? false,
    message: extra.message
  })
}

function parseEpicSources(): string[] {
  const found: string[] = []
  const ingest = (raw: string) => {
    for (const hint of collectHintsFromManifestText(raw)) {
      found.push(...executablesForInstall(hint.installLocation, hint.launchExecutable))
    }
  }

  const installed = launcherInstalledPath()
  if (installed) {
    try {
      ingest(readFileSync(installed, 'utf8'))
    } catch {
      // Optional catalog.
    }
  }

  for (const dir of epicManifestDirs()) {
    try {
      for (const file of readdirSync(dir)) {
        if (!file.endsWith('.item') && !file.endsWith('.json') && !file.endsWith('.dat')) continue
        ingest(readFileSync(path.join(dir, file), 'utf8'))
      }
    } catch {
      // Manifests are optional hints.
    }
  }
  return found
}

async function readWindowsVersion(filePath: string): Promise<string | null> {
  if (!isWindows) return null
  try {
    const script = `
      $p = '${filePath.replace(/'/g, "''")}'
      try {
        $v = [System.Diagnostics.FileVersionInfo]::GetVersionInfo($p)
        if ($v.FileVersion) { $v.FileVersion } else { $v.ProductVersion }
      } catch { '' }
    `
    const out = await runPowerShell(script)
    return out || null
  } catch {
    return null
  }
}

export async function validateFortnitePath(filePath: string): Promise<FortniteInstallInfo> {
  const check = looksLikeFortniteExecutable(filePath)
  if (!check.valid) {
    return { found: false, path: filePath, version: null, valid: false, source: 'browse', reason: check.reason }
  }
  const version = await readWindowsVersion(filePath)
  return {
    found: true,
    path: filePath,
    version,
    valid: true,
    source: 'browse'
  }
}

export async function detectFortniteInstall(): Promise<FortniteInstallInfo> {
  const config = loadConfig()
  if (config.fortnitePath && existsSync(config.fortnitePath)) {
    const validated = await validateFortnitePath(config.fortnitePath)
    if (validated.valid) {
      return { ...validated, source: 'config' }
    }
  }

  const candidates = rankFortniteCandidates([...parseEpicSources(), ...commonFortniteCandidates()])
  for (const candidate of candidates) {
    if (!existsSync(candidate)) continue
    const validated = await validateFortnitePath(candidate)
    if (validated.valid) {
      updateConfig({ fortnitePath: candidate, fortniteVersion: validated.version })
      return { ...validated, source: 'auto' }
    }
  }

  return {
    found: false,
    path: config.fortnitePath,
    version: config.fortniteVersion,
    valid: false,
    source: config.fortnitePath ? 'config' : null,
    reason:
      'Fortnite was not found in Epic manifests or common install folders. Browse to FortniteClient-Win64-Shipping.exe, FortniteBootstrapper.exe, or Fortnite.exe.'
  }
}

export async function persistFortnitePath(filePath: string): Promise<FortniteInstallInfo> {
  const info = await validateFortnitePath(filePath)
  if (info.valid) {
    updateConfig({ fortnitePath: info.path, fortniteVersion: info.version })
  }
  return info
}

export async function listFortnitePids(): Promise<number[]> {
  if (!isWindows) {
    return trackedPid ? [trackedPid] : []
  }
  try {
    const names = FORTNITE_PROCESS_NAMES.map((n) => `'${n.replace(/\.exe$/i, '')}'`).join(',')
    const script = `
      $names = @(${names})
      Get-Process -ErrorAction SilentlyContinue | Where-Object { $names -contains $_.ProcessName } | Select-Object -ExpandProperty Id
    `
    const out = await runPowerShell(script)
    return out
      .split(/\s+/)
      .map((v) => Number(v))
      .filter((n) => Number.isFinite(n) && n > 0)
  } catch {
    return []
  }
}

export async function isFortniteRunning(): Promise<boolean> {
  const pids = await listFortnitePids()
  return pids.length > 0
}

export async function isFortniteFocused(): Promise<boolean> {
  if (!isWindows) return status === 'RUNNING'
  try {
    const script = `
      Add-Type @"
        using System;
        using System.Runtime.InteropServices;
        using System.Text;
        public class NauticalFocus {
          [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
          [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
        }
"@
      $hwnd = [NauticalFocus]::GetForegroundWindow()
      $procId = 0
      [void][NauticalFocus]::GetWindowThreadProcessId($hwnd, [ref]$procId)
      if ($procId -eq 0) { 'false'; exit }
      $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
      if ($p -and ($p.ProcessName -match 'Fortnite')) { 'true' } else { 'false' }
    `
    const out = await runPowerShell(script)
    return out.trim().toLowerCase() === 'true'
  } catch {
    return false
  }
}

export async function launchExecutable(filePath: string): Promise<{ pid: number | null; message: string }> {
  if (!existsSync(filePath)) {
    throw new Error('Fortnite path no longer exists.')
  }
  const child = spawn(filePath, [], {
    detached: true,
    stdio: 'ignore',
    cwd: path.dirname(filePath),
    windowsHide: false
  })
  child.unref()
  trackedPid = child.pid ?? null
  startedAt = Date.now()
  return { pid: trackedPid, message: 'Fortnite launch requested.' }
}

export function startProcessPoll(onExit: (unexpected: boolean) => void): void {
  stopProcessPoll()
  pollTimer = setInterval(async () => {
    if (status !== 'RUNNING' && status !== 'LAUNCHING') return
    const running = await isFortniteRunning()
    if (!running && status === 'RUNNING') {
      onExit(true)
    }
  }, 2500)
}

export function stopProcessPoll(): void {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

export function markLaunched(pid: number | null): void {
  trackedPid = pid
  startedAt = Date.now()
  setStatus('RUNNING')
}

export function fileSizeHint(filePath: string): number | null {
  try {
    return statSync(filePath).size
  } catch {
    return null
  }
}
