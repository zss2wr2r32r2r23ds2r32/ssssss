import { getActiveProfile } from '../../shared/defaults'
import { normalizeLaunchMethod } from '../../shared/fortnite-launch'
import type { LaunchResult } from '../../shared/types'
import { loadConfig } from './config'
import {
  findEpicLauncher,
  getLaunchStatus,
  isFortniteRunning,
  listEpicLauncherPids,
  listLaunchHelperPids,
  markLaunched,
  onStatus,
  setStatus,
  startFortniteViaMethod,
  startProcessPoll,
  stopProcessPoll,
  waitForGameProcess
} from './fortnite'
import { startMacro, stopMacro } from './macro'
import { startOverlay, stopOverlay, recenterOverlay } from './overlay'
import { applyCleanup, restoreCleanup } from './performance'
import { applyResolution, restoreTemporaryIfNeeded } from './resolution'
import { hostHideEpic } from './win32-host'

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
}

export async function launchFromActiveProfile(): Promise<LaunchResult> {
  const current = loadConfig()
  const status = getLaunchStatus()
  if (status === 'LAUNCHING') {
    return {
      ok: false,
      status: 'LAUNCHING',
      message: 'Avix is already starting Fortnite.'
    }
  }
  if (status === 'RUNNING' || (await isFortniteRunning())) {
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
  const launchMethod = normalizeLaunchMethod(current.general.launchMethod)
  setStatus('LAUNCHING')

  try {
    let resolutionNote = ''
    if (profile.resolution.applyOnLaunch) {
      try {
        const applied = await applyResolution(profile.resolution, false)
        if (!applied.ok) {
          resolutionNote = ` Display mode was not applied (${applied.message}). Fortnite still starts.`
        }
      } catch (error) {
        resolutionNote = ` Display helper was unavailable (${error instanceof Error ? error.message : 'unknown'}). Fortnite still starts.`
      }
    } else if (profile.resolution.applyGameUserSettings && profile.resolution.method === 'fortnite-only') {
      try {
        await applyResolution({ ...profile.resolution, applyOnLaunch: true, method: 'fortnite-only' }, false)
      } catch {
        /* GameUserSettings is optional. */
      }
    }

    if (profile.performance.cleanupOnLaunch && profile.performance.selectedApps.length) {
      await applyCleanup(profile.performance.selectedApps)
    }

    const started = await startFortniteViaMethod(path, launchMethod)
    if (!started.ok) {
      setStatus('NOT_RUNNING')
      return {
        ok: false,
        status: 'NOT_RUNNING',
        code: started.code ?? 'SPAWN_FAILED',
        message: started.message
      }
    }

    const pid = await waitForGameProcess(75000)
    if (!pid) {
      const helpers = await listLaunchHelperPids()
      const epicPids = await listEpicLauncherPids()
      const epicInstalled = Boolean(findEpicLauncher())
      setStatus('NOT_RUNNING')
      if (!epicInstalled && launchMethod === 'epic-uri') {
        return {
          ok: false,
          status: 'NOT_RUNNING',
          code: 'EPIC_REQUIRED',
          message: 'Epic Games Launcher was not found. Install Epic, sign in, then launch again — or set launch method to Bootstrapper.'
        }
      }
      if (helpers.length === 0 && epicPids.length === 0) {
        return {
          ok: false,
          status: 'NOT_RUNNING',
          code: 'AUTH_REQUIRED',
          message:
            'Fortnite did not start. Stay signed into Epic Games Launcher (Avix does not bypass Epic login), then try Launch again.'
        }
      }
      return {
        ok: false,
        status: 'NOT_RUNNING',
        code: 'LAUNCH_TIMEOUT',
        message:
          'A helper started but FortniteClient-Win64-Shipping.exe never stayed running. Sign into Epic, accept any update, then retry.'
      }
    }

    markLaunched(pid)
    startProcessPoll((unexpected) => {
      void teardown(
        unexpected,
        unexpected ? 'Fortnite closed unexpectedly. Overlay, macro, and temporary display/config were restored.' : undefined
      )
    })

    if (current.general.hideEpicAfterLaunch !== false) {
      void hostHideEpic()
      setTimeout(() => {
        void hostHideEpic()
      }, 2500)
    }

    void recenterOverlay()
    setTimeout(() => {
      void recenterOverlay()
    }, 1500)

    if (profile.crosshair.enabled) {
      try {
        await startOverlay(profile.crosshair)
      } catch {
        return {
          ok: true,
          status: 'RUNNING',
          code: 'OVERLAY_FAILED',
          message: 'Fortnite is running, but the crosshair overlay failed to start.'
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
          message: `Fortnite is running. Macro did not start: ${macro.message}`
        }
      }
    }

    const base =
      started.used === 'bootstrapper'
        ? 'FortniteBootstrapper handed off. Fortnite is running. Epic login was not bypassed.'
        : started.used === 'shipping'
          ? 'Shipping executable is running. If it closes next time, stay signed into Epic or use Bootstrapper.'
          : 'Fortnite is running.'
    return {
      ok: true,
      status: 'RUNNING',
      code: resolutionNote ? 'APPLY_FAILED' : undefined,
      message: `${base}${resolutionNote}`
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
