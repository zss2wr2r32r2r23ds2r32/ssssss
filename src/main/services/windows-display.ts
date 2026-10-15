import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { isWindows } from './windows-api'
import { win32Resource } from './win32-resources'

const execFileAsync = promisify(execFile)

export interface DisplayMode {
  width: number
  height: number
  freq: number
}

function parseModes(raw: string): DisplayMode[] {
  if (!raw) return []
  return raw
    .split(';')
    .map((part) => {
      const m = part.match(/^(\d+)x(\d+)@(\d+)$/)
      if (!m) return null
      return { width: Number(m[1]), height: Number(m[2]), freq: Number(m[3]) }
    })
    .filter((x): x is DisplayMode => Boolean(x))
}

async function runDisplay(args: string[], timeout = 8000): Promise<string> {
  if (!isWindows) throw new Error('Display helper is Windows-only.')
  const script = win32Resource('avix-display.ps1')
  if (!script) throw new Error('Display helper is missing from Avix resources. Rebuild/reinstall the app.')
  const { stdout } = await execFileAsync(
    'powershell.exe',
    ['-NoProfile', '-STA', '-ExecutionPolicy', 'Bypass', '-File', script, ...args],
    { timeout, windowsHide: true, maxBuffer: 2 * 1024 * 1024 }
  )
  return stdout.trim()
}

export async function displayListModes(device = ''): Promise<DisplayMode[]> {
  try {
    return parseModes(await runDisplay(['MODES', device], 6000))
  } catch {
    return []
  }
}

export async function displayCurrentMode(
  device = '',
): Promise<{ width: number; height: number; freq: number; device: string } | null> {
  try {
    const raw = await runDisplay(['CURRENT', device], 4000)
    if (!raw.includes('x')) return null
    const [wh, deviceName] = raw.split('|')
    const m = wh.match(/^(\d+)x(\d+)@(\d+)$/)
    if (!m) return null
    return { width: Number(m[1]), height: Number(m[2]), freq: Number(m[3]), device: deviceName || '' }
  } catch {
    return null
  }
}

export async function displayChange(device: string, width: number, height: number, freq: number): Promise<string> {
  return runDisplay(['APPLY', String(width), String(height), String(freq), device], 8000)
}

export async function displayRestore(device = ''): Promise<string> {
  return runDisplay(['RESTORE', device], 6000)
}

export async function displayDeviceAt(x: number, y: number): Promise<string> {
  try {
    return (await runDisplay(['DEVICEAT', String(x), String(y)], 4000)).trim()
  } catch {
    return ''
  }
}

export async function nvidiaProbe(): Promise<'OK' | 'MISSING' | string> {
  try {
    return (await runDisplay(['NVIDIA'], 5000)) as 'OK' | 'MISSING' | string
  } catch {
    return 'MISSING'
  }
}

export function displayHelperPresent(): boolean {
  return Boolean(win32Resource('avix-display.ps1'))
}
