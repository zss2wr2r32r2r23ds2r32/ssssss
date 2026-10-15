import type { CleanupCandidate, OperationResult } from '../../shared/types'
import { EPIC_AUTH_PROCESS_NAMES, FORTNITE_PROCESS_NAMES, isWindows, runPowerShell } from './windows-api'

const PROTECTED_PATTERNS = [
  /^csrss$/i,
  /^wininit$/i,
  /^winlogon$/i,
  /^services$/i,
  /^lsass$/i,
  /^smss$/i,
  /^svchost$/i,
  /^dwm$/i,
  /^fontdrvhost$/i,
  /^audiodg$/i,
  /^system$/i,
  /^registry$/i,
  /^memory compression$/i,
  /^secure system$/i,
  /^explorer$/i,
  /^taskmgr$/i,
  /^sihost$/i,
  /^ctfmon$/i,
  /^runtimebroker$/i,
  /^searchhost$/i,
  /^startmenuexperiencehost$/i,
  /^textinputhost$/i,
  /^nvidia/i,
  /^nvcontainer/i,
  /^nvdisplay/i,
  /^nvxdsync/i,
  /^amddvr/i,
  /^amdow/i,
  /^radeon/i,
  /^atiesrxx/i,
  /^igfx/i,
  /^intelgraphics/i,
  /^msmpeng$/i,
  /^nissrv$/i,
  /^securityhealth/i,
  /^smartscreen$/i,
  /^mbam/i,
  /^avp$/i,
  /^avgui$/i,
  /^ekrn$/i,
  /^easyanticheat/i,
  /^beService$/i,
  /^battleye/i,
  ...FORTNITE_PROCESS_NAMES.map((n) => new RegExp(`^${n.replace(/\.exe$/i, '')}$`, 'i')),
  ...EPIC_AUTH_PROCESS_NAMES.map((n) => new RegExp(`^${n.replace(/\.exe$/i, '')}$`, 'i'))
]

const CATALOG: Array<Omit<CleanupCandidate, 'running' | 'pids' | 'memoryMb' | 'protected'>> = [
  { id: 'discord', name: 'Discord', description: 'Chat overlay and Chromium helpers', processes: ['Discord.exe', 'DiscordPTB.exe', 'DiscordCanary.exe'] },
  { id: 'chrome', name: 'Google Chrome', description: 'Browser tabs and GPU process', processes: ['chrome.exe'] },
  { id: 'edge', name: 'Microsoft Edge', description: 'Browser tabs', processes: ['msedge.exe'] },
  { id: 'firefox', name: 'Firefox', description: 'Browser content processes', processes: ['firefox.exe'] },
  { id: 'spotify', name: 'Spotify', description: 'Music client', processes: ['Spotify.exe'] },
  { id: 'steam', name: 'Steam (client UI)', description: 'Steam client only — never game or service hosts you did not pick', processes: ['steam.exe'] },
  { id: 'slack', name: 'Slack', description: 'Workspace client', processes: ['slack.exe'] },
  { id: 'obs', name: 'OBS Studio', description: 'Streaming / recording', processes: ['obs64.exe', 'obs32.exe'] },
  { id: 'spotify-web', name: 'Spotify WebView extras', description: 'Helper processes if present', processes: ['SpotifyWebHelper.exe'] },
  { id: 'opera', name: 'Opera', description: 'Browser', processes: ['opera.exe'] },
  { id: 'brave', name: 'Brave', description: 'Browser', processes: ['brave.exe'] },
  { id: 'vlc', name: 'VLC', description: 'Media player', processes: ['vlc.exe'] }
]

interface ClosedApp {
  id: string
  name: string
  exe: string | null
}

let closed: ClosedApp[] = []

export function isProtectedProcess(name: string): boolean {
  const base = name.replace(/\.exe$/i, '')
  return PROTECTED_PATTERNS.some((pattern) => pattern.test(base))
}

interface ProcessRow {
  name: string
  pid: number
  memoryMb: number
  path: string | null
}

async function listProcesses(): Promise<ProcessRow[]> {
  if (!isWindows) return []
  const script = `
    Get-CimInstance Win32_Process | Select-Object ProcessId, Name, WorkingSetSize, ExecutablePath | ConvertTo-Json -Compress
  `
  try {
    const raw = await runPowerShell(script, 20000)
    const parsed = JSON.parse(raw) as Array<{ ProcessId: number; Name: string; WorkingSetSize: number; ExecutablePath?: string }>
    return parsed.map((row) => ({
      name: row.Name,
      pid: row.ProcessId,
      memoryMb: Math.round((row.WorkingSetSize || 0) / 1024 / 1024),
      path: row.ExecutablePath ?? null
    }))
  } catch {
    return []
  }
}

export async function listCandidates(): Promise<CleanupCandidate[]> {
  const running = await listProcesses()
  return CATALOG.map((item) => {
    const matches = running.filter((row) =>
      item.processes.some((proc) => proc.toLowerCase() === row.name.toLowerCase())
    )
    const protectedHit = matches.some((row) => isProtectedProcess(row.name))
    return {
      ...item,
      running: matches.length > 0,
      pids: matches.map((m) => m.pid),
      memoryMb: matches.reduce((sum, m) => sum + m.memoryMb, 0),
      protected: protectedHit
    }
  })
}

export async function applyCleanup(selectedIds: string[]): Promise<OperationResult<{ closed: ClosedApp[] }>> {
  if (!isWindows) {
    return { ok: false, message: 'Gaming cleanup is available on Windows only.', data: { closed: [] } }
  }
  const candidates = await listCandidates()
  const chosen = candidates.filter((c) => selectedIds.includes(c.id) && c.running && !c.protected)
  const running = await listProcesses()
  const justClosed: ClosedApp[] = []

  for (const app of chosen) {
    for (const procName of app.processes) {
      const rows = running.filter((r) => r.name.toLowerCase() === procName.toLowerCase())
      for (const row of rows) {
        if (isProtectedProcess(row.name)) continue
        try {
          await runPowerShell(`Stop-Process -Id ${row.pid} -ErrorAction SilentlyContinue`)
          justClosed.push({ id: app.id, name: app.name, exe: row.path })
        } catch {
          // Keep going; one process may already have exited.
        }
      }
    }
  }

  closed = justClosed
  return {
    ok: true,
    message: justClosed.length
      ? `Closed ${justClosed.length} background process(es). This frees CPU/RAM; it is not an FPS guarantee.`
      : 'No matching non-protected processes were running.',
    data: { closed: justClosed }
  }
}

export async function restoreCleanup(): Promise<OperationResult> {
  if (!closed.length) {
    return { ok: true, message: 'No cleanup session to restore.' }
  }
  let launched = 0
  for (const app of closed) {
    if (!app.exe) continue
    try {
      await runPowerShell(`Start-Process -FilePath '${app.exe.replace(/'/g, "''")}'`)
      launched += 1
    } catch {
      // Best-effort restore only.
    }
  }
  closed = []
  return { ok: true, message: launched ? `Attempted to relaunch ${launched} app(s).` : 'Nothing to relaunch.' }
}

export function resetCleanupSession(): void {
  closed = []
}
