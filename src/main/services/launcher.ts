import { getActiveProfile } from '../../shared/defaults'
import type { LaunchResult } from '../../shared/types'
import { loadConfig } from './config'
import {
  getLaunchStatus,
  isFortniteRunning,
  launchExecutable,
  listFortnitePids,
  markLaunched,
  onStatus,
  setStatus,
  startProcessPoll,
  stopProcessPoll
} from './fortnite'
import { startMacro, stopMacro } from './macro'
import { applyCleanup, restoreCleanup } from './performance'
import { applyResolution, restoreTemporaryIfNeeded } from './resolution'
import { startOverlay, stopOverlay } from './overlay'

let shuttingDown = false

async function teardown(unexpected: boolean, message?: string): Promise<void> {
  if (shuttingDown) return
  shuttingDown = true
  setStatus('CLOSING', { unexpected, message })
  stopProcessPoll()
  stopOverlay()
  stopMacro()
  const profile = getActiveProfile(loadConfig())
  await restoreTemporaryIfNeeded()
  if (profile.performance.restoreAfterExit) {
    await restoreCleanup()
  }
  setStatus('CLOSED', { unexpected, message })
  setTimeout(() => {
    setStatus('NOT_RUNNING', { unexpected: false })
    shuttingDown = false
  }, 800)
}

export function attachExitHandler(): void {
  onStatus(() => undefined)
  startProcessPoll((unexpected) => {
    void teardown(unexpected, unexpected ? 'Fortnite closed unexpectedly. Overlay, macro, and temporary display/config were restored.' : undefined)
  })
}

export async function launchFromActiveProfile(): Promise<LaunchResult> {
  const current = loadConfig()
  const status = getLaunchStatus()
  if (status === 'RUNNING' || status === 'LAUNCHING' || (await isFortniteRunning())) {
    return {
      ok: false,
      status: 'RUNNING',
      code: 'ALREADY_RUNNING',
      message: 'Fortnite is already running. Avix will not start a second session.'
    }
  }

  const path = current.fortnitePath
  if (!path) {
    return {
      ok: false,
      status: 'NOT_RUNNING',
      code: 'MISSING_INSTALL',
      message: 'Fortnite is not configured. Detect or browse to Fortnite.exe first.'
    }
  }

  const profile = getActiveProfile(current)
  setStatus('LAUNCHING')

  try {
    if (profile.resolution.applyOnLaunch) {
      const applied = await applyResolution(profile.resolution, false)
      if (!applied.ok) {
        setStatus('NOT_RUNNING')
        return {
          ok: false,
          status: 'NOT_RUNNING',
          code: 'APPLY_FAILED',
          message: applied.message
        }
      }
    } else if (profile.resolution.applyGameUserSettings) {
      const applied = await applyResolution(
        { ...profile.resolution, applyOnLaunch: true, method: 'fortnite-only' },
        false
      )
      if (!applied.ok && applied.code !== 'APPLY_FAILED') {
        // Missing GameUserSettings is not fatal; Fortnite can still start.
      }
    }

    if (profile.performance.cleanupOnLaunch && profile.performance.selectedApps.length) {
      await applyCleanup(profile.performance.selectedApps)
    }

    const launched = await launchExecutable(path)
    const pids = await listFortnitePids()
    markLaunched(launched.pid ?? pids[0] ?? null)
    startProcessPoll((unexpected) => {
      void teardown(unexpected, unexpected ? 'Fortnite closed unexpectedly. Overlay, macro, and temporary display/config were restored.' : undefined)
    })

    if (profile.crosshair.enabled) {
      try {
        if (!profile.crosshair.onlyWhileFortnite) {
          await startOverlay(profile.crosshair)
        } else {
          await startOverlay(profile.crosshair)
        }
      } catch {
        return {
          ok: true,
          status: 'RUNNING',
          code: 'OVERLAY_FAILED',
          message: 'Fortnite launched, but the crosshair overlay failed to start.'
        }
      }
    }

    if (profile.macro.enabled) {
      const macro = startMacro(profile.macro)
      if (!macro.ok) {
        return {
          ok: true,
          status: 'RUNNING',
          code: macro.code === 'MACRO_NOT_ACKNOWLEDGED' ? 'MACRO_NOT_ACKNOWLEDGED' : 'MACRO_FAILED',
          message: `Fortnite launched. Macro did not start: ${macro.message}`
        }
      }
    }

    return {
      ok: true,
      status: 'RUNNING',
      message: 'Fortnite launch sequence started.'
    }
  } catch (error) {
    setStatus('NOT_RUNNING')
    return {
      ok: false,
      status: 'NOT_RUNNING',
      code: 'SPAWN_FAILED',
      message: error instanceof Error ? error.message : 'Could not start Fortnite.'
    }
  }
}

export async function handleFortniteExit(unexpected: boolean): Promise<void> {
  await teardown(unexpected)
}
