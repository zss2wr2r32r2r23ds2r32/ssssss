import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { existsSync } from 'node:fs'
import path from 'node:path'
import {
  commonFortniteCandidatePaths,
  looksLikeFortniteName,
  PREFERRED_FORTNITE_EXES
} from '../../shared/fortnite-detect'

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
  return commonFortniteCandidatePaths(process.env)
}

export function epicManifestDirs(): string[] {
  const programData = process.env.PROGRAMDATA ?? 'C:\\ProgramData'
  return [
    path.join(programData, 'Epic', 'EpicGamesLauncher', 'Data', 'Manifests')
  ].filter((dir) => existsSync(dir))
}

export function launcherInstalledPath(): string | null {
  const programData = process.env.PROGRAMDATA ?? 'C:\\ProgramData'
  const file = path.join(programData, 'Epic', 'UnrealEngineLauncher', 'LauncherInstalled.dat')
  return existsSync(file) ? file : null
}

export function fortniteBrowseStartDir(): string | undefined {
  const guesses = commonFortniteCandidatePaths(process.env)
    .map((file) => path.dirname(file))
    .filter((dir) => existsSync(dir))
  return guesses[0]
}

export const FORTNITE_DIALOG_FILTERS = [
  { name: 'FortniteClient-Win64-Shipping.exe', extensions: ['exe'] },
  { name: 'FortniteBootstrapper.exe', extensions: ['exe'] },
  { name: 'Fortnite.exe', extensions: ['exe'] },
  { name: 'Fortnite executables', extensions: ['exe'] }
]

export function fortniteConfigPath(): string | null {
  const local = process.env.LOCALAPPDATA
  if (!local) return null
  return path.join(local, 'FortniteGame', 'Saved', 'Config', 'WindowsClient', 'GameUserSettings.ini')
}

export const FORTNITE_PROCESS_NAMES = [
  'FortniteClient-Win64-Shipping.exe',
  'FortniteClient-Win64-Shipping_EAC.exe',
  'FortniteClient-Win64-Shipping_BE.exe',
  'FortniteBootstrapper.exe',
  'Fortnite.exe',
  'FortniteLauncher.exe'
]

export const EPIC_AUTH_PROCESS_NAMES = [
  'EpicGamesLauncher.exe',
  'EpicWebHelper.exe',
  'EpicGamesLauncher-Win64-Shipping.exe'
]

export function looksLikeFortniteExecutable(filePath: string): { valid: boolean; reason?: string } {
  const named = looksLikeFortniteName(filePath)
  if (!named.valid) return named
  if (!existsSync(filePath)) {
    return { valid: false, reason: 'File does not exist.' }
  }
  return { valid: true }
}

export { PREFERRED_FORTNITE_EXES }
