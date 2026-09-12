import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { existsSync } from 'node:fs'
import path from 'node:path'

const execFileAsync = promisify(execFile)

export const isWindows = process.platform === 'win32'

export function expandEnv(input: string): string {
  return input.replace(/%([^%]+)%/g, (_, key: string) => process.env[key] ?? '')
}

export async function runPowerShell(script: string, timeout = 15000): Promise<string> {
  if (!isWindows) {
    throw new Error('PowerShell helpers are Windows-only.')
  }
  const encoded = Buffer.from(script, 'utf16le').toString('base64')
  const { stdout } = await execFileAsync(
    'powershell.exe',
    ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', encoded],
    { timeout, windowsHide: true, maxBuffer: 4 * 1024 * 1024 }
  )
  return stdout.trim()
}

export async function runCmd(args: string[], timeout = 10000): Promise<string> {
  const { stdout } = await execFileAsync(args[0], args.slice(1), {
    timeout,
    windowsHide: true,
    maxBuffer: 2 * 1024 * 1024
  })
  return stdout.trim()
}

export function commonFortniteCandidates(): string[] {
  const drives = ['C', 'D', 'E', 'F']
  const roots = [
    'Program Files\\Epic Games\\Fortnite\\FortniteGame\\Binaries\\Win64',
    'Program Files (x86)\\Epic Games\\Fortnite\\FortniteGame\\Binaries\\Win64',
    'Epic Games\\Fortnite\\FortniteGame\\Binaries\\Win64',
    'Games\\Epic Games\\Fortnite\\FortniteGame\\Binaries\\Win64',
    'Fortnite\\FortniteGame\\Binaries\\Win64'
  ]
  const exes = ['FortniteClient-Win64-Shipping.exe', 'Fortnite.exe']
  const out: string[] = []
  for (const drive of drives) {
    for (const root of roots) {
      for (const exe of exes) {
        out.push(`${drive}:\\${root}\\${exe}`)
      }
    }
  }
  const local = process.env.LOCALAPPDATA
  if (local) {
    out.push(path.join(local, 'FortniteGame', 'Saved', 'StagedBuilds'))
  }
  return out
}

export function epicManifestDir(): string | null {
  const programData = process.env.PROGRAMDATA ?? 'C:\\ProgramData'
  const dir = path.join(programData, 'Epic', 'EpicGamesLauncher', 'Data', 'Manifests')
  return existsSync(dir) ? dir : null
}

export function fortniteConfigPath(): string | null {
  const local = process.env.LOCALAPPDATA
  if (!local) return null
  return path.join(local, 'FortniteGame', 'Saved', 'Config', 'WindowsClient', 'GameUserSettings.ini')
}

export const FORTNITE_PROCESS_NAMES = [
  'FortniteClient-Win64-Shipping.exe',
  'FortniteClient-Win64-Shipping_EAC.exe',
  'FortniteClient-Win64-Shipping_BE.exe',
  'Fortnite.exe',
  'FortniteLauncher.exe'
]

export const EPIC_AUTH_PROCESS_NAMES = [
  'EpicGamesLauncher.exe',
  'EpicWebHelper.exe',
  'EpicGamesLauncher-Win64-Shipping.exe'
]

export function looksLikeFortniteExecutable(filePath: string): { valid: boolean; reason?: string } {
  const base = path.basename(filePath).toLowerCase()
  if (!base.endsWith('.exe')) {
    return { valid: false, reason: 'Selected file is not an .exe' }
  }
  const fortniteNamed =
    base.includes('fortnite') ||
    base === 'fortniteclient-win64-shipping.exe'
  const parent = filePath.toLowerCase()
  const inFortniteTree =
    parent.includes('fortnitegame') ||
    parent.includes('\\fortnite\\') ||
    parent.includes('/fortnite/')
  if (!fortniteNamed && !inFortniteTree) {
    return {
      valid: false,
      reason: 'File does not look like Fortnite (name or install folder mismatch).'
    }
  }
  if (!existsSync(filePath)) {
    return { valid: false, reason: 'File does not exist.' }
  }
  return { valid: true }
}
