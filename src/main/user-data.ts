import { app } from 'electron'
import { copyFileSync, existsSync, mkdirSync, readdirSync } from 'node:fs'
import path from 'node:path'

export const APP_USERDATA_NAME = 'avix-launcher'

const MIGRATE_FILES = ['avix-config.json', 'nautical-config.json', 'profile-picture.png', 'profile-picture.jpg', 'profile-picture.jpeg', 'profile-picture.webp']

function collectLegacyDirs(): string[] {
  const dirs: string[] = []
  try {
    dirs.push(app.getPath('userData'))
  } catch {
    // Path may not be readable yet.
  }
  const portable = process.env.PORTABLE_EXECUTABLE_DIR
  if (portable) {
    dirs.push(
      path.join(portable, 'Avix Launcher-data'),
      path.join(portable, 'avix-launcher-data'),
      path.join(portable, 'AvixLauncher-data')
    )
  }
  return dirs.filter((dir, index, all) => dir && all.indexOf(dir) === index)
}

export function stabilizeUserData(): string {
  const stable = path.join(app.getPath('appData'), APP_USERDATA_NAME)
  mkdirSync(stable, { recursive: true })
  app.setPath('userData', stable)
  return stable
}

export function migrateUserDataIfNeeded(): string {
  const stable = app.getPath('userData')
  mkdirSync(stable, { recursive: true })
  const hasAvix = existsSync(path.join(stable, 'avix-config.json'))
  for (const dir of collectLegacyDirs()) {
    if (dir.toLowerCase() === stable.toLowerCase() || !existsSync(dir)) continue
    for (const file of MIGRATE_FILES) {
      const from = path.join(dir, file)
      const to = path.join(stable, file)
      if (!existsSync(from) || existsSync(to)) continue
      if (file === 'nautical-config.json' && hasAvix) continue
      try {
        copyFileSync(from, to)
      } catch {
        // Best-effort migrate.
      }
    }
    try {
      for (const file of readdirSync(dir)) {
        if (!file.startsWith('profile-picture.')) continue
        const to = path.join(stable, file)
        if (!existsSync(to)) copyFileSync(path.join(dir, file), to)
      }
    } catch {
      // Ignore unreadable legacy folders.
    }
  }
  return stable
}
