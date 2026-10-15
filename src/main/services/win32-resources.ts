import { app } from 'electron'
import { existsSync } from 'node:fs'
import { join } from 'node:path'

export function win32Resource(fileName: string): string | null {
  const candidates = [
    join(process.resourcesPath ?? '', 'resources', 'win32', fileName),
    join(process.resourcesPath ?? '', 'win32', fileName),
    join(app.getAppPath(), 'resources', 'win32', fileName),
    join(__dirname, '../../resources/win32', fileName),
    join(process.cwd(), 'resources', 'win32', fileName)
  ]
  return candidates.find((file) => file && existsSync(file)) ?? null
}
