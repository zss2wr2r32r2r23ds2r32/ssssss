import { app } from 'electron'
import type { GeneralSettings } from '../../shared/types'

export function applyLoginItem(general: GeneralSettings): void {
  if (process.platform !== 'win32' && process.platform !== 'darwin') return
  app.setLoginItemSettings({
    openAtLogin: general.startWithWindows,
    enabled: general.startWithWindows,
    args: general.startMinimized ? ['--hidden'] : []
  })
}

export function syncHardwareAccelerationNote(_enabled: boolean): void {
  // Hardware acceleration is applied at process start in index.ts.
}

export function shouldStartHidden(): boolean {
  return process.argv.includes('--hidden')
}
