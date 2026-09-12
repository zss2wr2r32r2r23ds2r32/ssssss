export const FORTNITE_GAME_IMAGES = [
  'FortniteClient-Win64-Shipping.exe',
  'FortniteClient-Win64-Shipping_EAC.exe',
  'FortniteClient-Win64-Shipping_EAC_EOS.exe',
  'FortniteClient-Win64-Shipping_BE.exe'
] as const

export const FORTNITE_HELPER_IMAGES = [
  'FortniteBootstrapper.exe',
  'FortniteLauncher.exe',
  'Fortnite.exe'
] as const

export const EPIC_LAUNCHER_IMAGES = [
  'EpicGamesLauncher.exe',
  'EpicGamesLauncher-Win64-Shipping.exe'
] as const

export const PROCESS_POLL_WHEN_RUNNING_MS = 10_000
export const PROCESS_WAIT_INTERVAL_MS = 2_000

function normalizeImage(name: string): string {
  return name.replace(/\.exe$/i, '').toLowerCase()
}

/** Parse `tasklist /FO CSV /NH` output and return PIDs whose image is in `names`. */
export function parseTasklistCsv(stdout: string, names: readonly string[]): number[] {
  const want = new Set(names.map(normalizeImage))
  const ids: number[] = []
  for (const line of stdout.split(/\r?\n/)) {
    const match = line.match(/^"([^"]+)"\s*,\s*"(\d+)"/)
    if (!match) continue
    if (!want.has(normalizeImage(match[1]))) continue
    const pid = Number(match[2])
    if (Number.isFinite(pid) && pid > 0) ids.push(pid)
  }
  return ids
}
