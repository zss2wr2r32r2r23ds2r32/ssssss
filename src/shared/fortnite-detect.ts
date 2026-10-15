export const PREFERRED_FORTNITE_EXES = [
  'FortniteClient-Win64-Shipping.exe',
  'FortniteBootstrapper.exe',
  'Fortnite.exe'
] as const

const ALLOWED_EXE_NAMES = new Set(
  [
    ...PREFERRED_FORTNITE_EXES,
    'FortniteLauncher.exe',
    'FortniteClient-Win64-Shipping_EAC.exe',
    'FortniteClient-Win64-Shipping_BE.exe'
  ].map((name) => name.toLowerCase())
)

export interface EpicInstallHint {
  installLocation: string
  launchExecutable: string | null
  appName: string | null
  displayName: string | null
}

export function normalizeWindowsPath(value: string): string {
  return value
    .replace(/^\uFEFF/, '')
    .replace(/^["']+|["']+$/g, '')
    .replace(/\\\\/g, '\\')
    .replace(/\//g, '\\')
    .trim()
}

export function joinWin(...parts: string[]): string {
  return parts
    .map((part, index) => {
      const normalized = normalizeWindowsPath(part)
      if (index === 0) return normalized.replace(/\\+$/g, '')
      return normalized.replace(/^\\+/g, '').replace(/\\+$/g, '')
    })
    .filter(Boolean)
    .join('\\')
}

export function fortniteExecutableName(filePath: string): string {
  const normalized = normalizeWindowsPath(filePath)
  const parts = normalized.split('\\')
  return (parts[parts.length - 1] ?? '').toLowerCase()
}

export function isAllowedFortniteExecutableName(filePath: string): boolean {
  const base = fortniteExecutableName(filePath)
  if (!base.endsWith('.exe')) return false
  if (ALLOWED_EXE_NAMES.has(base)) return true
  const parent = normalizeWindowsPath(filePath).toLowerCase()
  const inFortniteTree =
    parent.includes('\\fortnitegame\\') ||
    parent.includes('\\fortnite\\') ||
    parent.endsWith('\\fortnite')
  return base.includes('fortnite') && inFortniteTree
}

export function looksLikeFortniteName(filePath: string): { valid: boolean; reason?: string } {
  const base = fortniteExecutableName(filePath)
  if (!base.endsWith('.exe')) {
    return { valid: false, reason: 'Selected file is not an .exe' }
  }
  if (!isAllowedFortniteExecutableName(filePath)) {
    return {
      valid: false,
      reason: 'File does not look like Fortnite (expected FortniteClient-Win64-Shipping.exe, FortniteBootstrapper.exe, or Fortnite.exe).'
    }
  }
  return { valid: true }
}

export function isFortniteHint(hint: EpicInstallHint): boolean {
  const blob = [hint.appName, hint.displayName, hint.installLocation, hint.launchExecutable]
    .filter(Boolean)
    .join(' ')
  return /fortnite/i.test(blob)
}

function asString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value : null
}

export function hintFromRecord(record: Record<string, unknown>): EpicInstallHint | null {
  const installLocation = asString(record.InstallLocation) ?? asString(record.installLocation)
  if (!installLocation) return null
  return {
    installLocation: normalizeWindowsPath(installLocation),
    launchExecutable: asString(record.LaunchExecutable) ?? asString(record.launchExecutable),
    appName: asString(record.AppName) ?? asString(record.appName) ?? asString(record.ArtifactId),
    displayName: asString(record.DisplayName) ?? asString(record.displayName)
  }
}

function unescapeJsonString(value: string): string {
  return value.replace(/\\"/g, '"').replace(/\\\\/g, '\\')
}

export function collectHintsFromManifestText(raw: string): EpicInstallHint[] {
  const text = raw.replace(/^\uFEFF/, '').trim()
  const hints: EpicInstallHint[] = []

  try {
    const data = JSON.parse(text) as Record<string, unknown>
    if (Array.isArray(data.InstallationList)) {
      for (const item of data.InstallationList) {
        if (item && typeof item === 'object') {
          const hint = hintFromRecord(item as Record<string, unknown>)
          if (hint) hints.push(hint)
        }
      }
    } else {
      const hint = hintFromRecord(data)
      if (hint) hints.push(hint)
    }
  } catch {
    const install = text.match(/"InstallLocation"\s*:\s*"((?:\\.|[^"\\])*)"/i)
    const launch = text.match(/"LaunchExecutable"\s*:\s*"((?:\\.|[^"\\])*)"/i)
    const appName = text.match(/"AppName"\s*:\s*"((?:\\.|[^"\\])*)"/i)
    const displayName = text.match(/"DisplayName"\s*:\s*"((?:\\.|[^"\\])*)"/i)
    if (install?.[1]) {
      hints.push({
        installLocation: normalizeWindowsPath(unescapeJsonString(install[1])),
        launchExecutable: launch?.[1] ? unescapeJsonString(launch[1]) : null,
        appName: appName?.[1] ? unescapeJsonString(appName[1]) : null,
        displayName: displayName?.[1] ? unescapeJsonString(displayName[1]) : null
      })
    }
  }

  return hints.filter(isFortniteHint)
}

export function executablesForInstall(installLocation: string, launchExecutable?: string | null): string[] {
  const root = normalizeWindowsPath(installLocation)
  const win64 = joinWin(root, 'FortniteGame', 'Binaries', 'Win64')
  const ordered: string[] = []
  const push = (candidate: string) => {
    const normalized = normalizeWindowsPath(candidate)
    if (!normalized) return
    if (ordered.some((existing) => existing.toLowerCase() === normalized.toLowerCase())) return
    ordered.push(normalized)
  }

  for (const exe of PREFERRED_FORTNITE_EXES) {
    push(joinWin(win64, exe))
  }
  if (launchExecutable) {
    push(joinWin(root, launchExecutable))
  }
  for (const exe of PREFERRED_FORTNITE_EXES) {
    push(joinWin(root, exe))
  }
  return ordered
}

export function commonInstallRoots(env: NodeJS.ProcessEnv = process.env): string[] {
  const roots = [
    env.ProgramFiles,
    env['ProgramFiles(x86)'],
    env.PROGRAMFILES,
    env['PROGRAMFILES(X86)']
  ].filter((value): value is string => Boolean(value))

  for (const drive of ['C', 'D', 'E', 'F', 'G']) {
    roots.push(
      `${drive}:\\Program Files`,
      `${drive}:\\Program Files (x86)`,
      `${drive}:\\Epic Games`,
      `${drive}:\\Games`
    )
  }

  const unique: string[] = []
  for (const root of roots.map(normalizeWindowsPath)) {
    if (!unique.some((existing) => existing.toLowerCase() === root.toLowerCase())) {
      unique.push(root)
    }
  }
  return unique
}

export function commonFortniteCandidatePaths(env: NodeJS.ProcessEnv = process.env): string[] {
  const suffixes = [
    ['Epic Games', 'Fortnite'],
    ['Fortnite'],
    ['Games', 'Epic Games', 'Fortnite']
  ]
  const out: string[] = []
  for (const root of commonInstallRoots(env)) {
    for (const suffix of suffixes) {
      out.push(...executablesForInstall(joinWin(root, ...suffix)))
    }
  }
  return out
}

export function rankFortniteCandidates(paths: string[]): string[] {
  const unique: string[] = []
  for (const filePath of paths) {
    const normalized = normalizeWindowsPath(filePath)
    if (!normalized.toLowerCase().endsWith('.exe')) continue
    if (unique.some((existing) => existing.toLowerCase() === normalized.toLowerCase())) continue
    unique.push(normalized)
  }
  const score = (filePath: string) => {
    const name = fortniteExecutableName(filePath)
    const index = PREFERRED_FORTNITE_EXES.findIndex((exe) => exe.toLowerCase() === name)
    return index === -1 ? PREFERRED_FORTNITE_EXES.length : index
  }
  return unique.sort((a, b) => score(a) - score(b))
}

export function isDebugPreviewMessage(text: string | undefined): boolean {
  if (!text) return false
  return /browser preview/i.test(text) || /from the packaged app/i.test(text)
}
