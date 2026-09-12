import { fortniteExecutableName, joinWin, normalizeWindowsPath } from './fortnite-detect'

export type LaunchMethod = 'bootstrapper' | 'shipping' | 'epic-uri'

export function normalizeLaunchMethod(value: unknown): LaunchMethod {
  if (value === 'shipping') return 'shipping'
  if (value === 'epic-uri' || value === 'epic') return 'epic-uri'
  return 'bootstrapper'
}

export const FORTNITE_EPIC_URI = 'com.epicgames.launcher://apps/Fortnite?action=launch'

export const FORTNITE_GAME_PROCESS_NAMES = [
  'FortniteClient-Win64-Shipping.exe',
  'FortniteClient-Win64-Shipping_EAC.exe',
  'FortniteClient-Win64-Shipping_BE.exe'
] as const

export const FORTNITE_HELPER_PROCESS_NAMES = [
  'FortniteBootstrapper.exe',
  'FortniteLauncher.exe',
  'Fortnite.exe'
] as const

export const EPIC_LAUNCHER_PROCESS_NAMES = [
  'EpicGamesLauncher.exe',
  'EpicGamesLauncher-Win64-Shipping.exe',
  'EpicWebHelper.exe'
] as const

function baseName(value: string): string {
  return fortniteExecutableName(value).replace(/\.exe$/i, '').toLowerCase()
}

export function isGameProcessName(name: string): boolean {
  const needle = baseName(name)
  return FORTNITE_GAME_PROCESS_NAMES.some((item) => baseName(item) === needle)
}

export function isLaunchHelperName(name: string): boolean {
  const needle = baseName(name)
  return FORTNITE_HELPER_PROCESS_NAMES.some((item) => baseName(item) === needle)
}

export function siblingNamedExe(filePath: string, fileName: string): string {
  const normalized = normalizeWindowsPath(filePath)
  const parts = normalized.split('\\')
  parts[parts.length - 1] = fileName
  return parts.join('\\')
}

export function siblingBootstrapper(filePath: string): string {
  return siblingNamedExe(filePath, 'FortniteBootstrapper.exe')
}

export function commonEpicLauncherPaths(env: NodeJS.ProcessEnv = process.env): string[] {
  const roots = [
    env.ProgramFiles,
    env['ProgramFiles(x86)'],
    env.PROGRAMFILES,
    env['PROGRAMFILES(X86)'],
    'C:\\Program Files',
    'C:\\Program Files (x86)'
  ].filter((value): value is string => Boolean(value))

  const unique: string[] = []
  for (const root of roots) {
    const candidate = joinWin(root, 'Epic Games', 'Launcher', 'Portal', 'Binaries', 'Win64', 'EpicGamesLauncher.exe')
    if (!unique.some((existing) => existing.toLowerCase() === candidate.toLowerCase())) {
      unique.push(candidate)
    }
  }
  return unique
}

export function resolveLaunchTargets(installPath: string | null): {
  shipping: string | null
  bootstrapper: string | null
} {
  if (!installPath) return { shipping: null, bootstrapper: null }
  const name = fortniteExecutableName(installPath)
  const shipping =
    name === 'fortniteclient-win64-shipping.exe' ? normalizeWindowsPath(installPath) : siblingNamedExe(installPath, 'FortniteClient-Win64-Shipping.exe')
  const bootstrapper =
    name === 'fortnitebootstrapper.exe' ? normalizeWindowsPath(installPath) : siblingBootstrapper(installPath)
  return { shipping, bootstrapper }
}

export function compareVersions(current: string, latest: string): number {
  const parse = (value: string) =>
    value
      .replace(/^v/i, '')
      .split('.')
      .map((part) => Number.parseInt(part.replace(/[^\d]/g, ''), 10) || 0)
  const a = parse(current)
  const b = parse(latest)
  const len = Math.max(a.length, b.length)
  for (let i = 0; i < len; i += 1) {
    const delta = (b[i] ?? 0) - (a[i] ?? 0)
    if (delta !== 0) return delta > 0 ? 1 : -1
  }
  return 0
}

export const GITHUB_REPO = 'zss2wr2r32r2r23ds2r32/ssssss'
export const GITHUB_RELEASES_URL = `https://github.com/${GITHUB_REPO}/releases`
export const GITHUB_LATEST_RELEASE_API = `https://api.github.com/repos/${GITHUB_REPO}/releases/latest`
export const GITHUB_TAGS_API = `https://api.github.com/repos/${GITHUB_REPO}/tags`
