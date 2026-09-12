import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import {
  EPIC_LAUNCHER_IMAGES,
  FORTNITE_GAME_IMAGES,
  FORTNITE_HELPER_IMAGES,
  parseTasklistCsv
} from '../../shared/process-list'
import { isWindows } from './windows-api'

const execFileAsync = promisify(execFile)
let cachedRaw = ''
let cachedAt = 0
const TASKLIST_TTL_MS = 1500

async function tasklistCsv(): Promise<string> {
  if (!isWindows) return ''
  if (Date.now() - cachedAt < TASKLIST_TTL_MS && cachedRaw) return cachedRaw
  try {
    const { stdout } = await execFileAsync('tasklist.exe', ['/FO', 'CSV', '/NH'], {
      timeout: 4000,
      windowsHide: true,
      maxBuffer: 2 * 1024 * 1024
    })
    cachedRaw = stdout
    cachedAt = Date.now()
    return stdout
  } catch {
    return cachedRaw
  }
}

export async function listPidsByImages(names: readonly string[]): Promise<number[]> {
  return parseTasklistCsv(await tasklistCsv(), names)
}

export async function listGamePids(): Promise<number[]> {
  return listPidsByImages(FORTNITE_GAME_IMAGES)
}

export async function listHelperPids(): Promise<number[]> {
  return listPidsByImages(FORTNITE_HELPER_IMAGES)
}

export async function listEpicPids(): Promise<number[]> {
  return listPidsByImages(EPIC_LAUNCHER_IMAGES)
}
