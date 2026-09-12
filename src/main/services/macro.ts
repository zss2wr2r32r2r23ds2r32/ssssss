import type { MacroSettings, OperationResult } from '../../shared/types'
import { ALLOWED_MACRO_KEYS } from '../../shared/types'
import { isFortniteFocused } from './fortnite'
import { isWindows, runPowerShell } from './windows-api'

let timer: NodeJS.Timeout | null = null
let running = false
let current: MacroSettings | null = null
let toggleOn = false
let activationHeld = false

const VIRTUAL_KEYS: Record<string, number> = {
  Space: 0x20,
  Tab: 0x09,
  Shift: 0x10,
  Ctrl: 0x11,
  Alt: 0x12,
  F1: 0x70,
  F2: 0x71,
  F3: 0x72,
  F4: 0x73,
  F5: 0x74,
  F6: 0x75,
  F7: 0x76,
  F8: 0x77,
  F9: 0x78,
  F10: 0x79,
  F11: 0x7a,
  F12: 0x7b
}

function vkFor(key: string): number | null {
  if (VIRTUAL_KEYS[key] !== undefined) return VIRTUAL_KEYS[key]
  if (/^[A-Z]$/.test(key)) return key.charCodeAt(0)
  if (/^[0-9]$/.test(key)) return 0x30 + Number(key)
  return null
}

function mouseFlag(button: MacroSettings['mouseButton'], down: boolean): number | null {
  if (button === 'left') return down ? 0x0002 : 0x0004
  if (button === 'right') return down ? 0x0008 : 0x0010
  if (button === 'middle') return down ? 0x0020 : 0x0040
  return null
}

async function sendOnce(settings: MacroSettings): Promise<void> {
  if (!isWindows) return
  const parts: string[] = []
  const vk = vkFor(settings.key)
  if (vk !== null) {
    parts.push(`
      [NauticalInput]::keybd_event(${vk}, 0, 0, [UIntPtr]::Zero)
      Start-Sleep -Milliseconds 12
      [NauticalInput]::keybd_event(${vk}, 0, 2, [UIntPtr]::Zero)
    `)
  }
  const down = mouseFlag(settings.mouseButton, true)
  const up = mouseFlag(settings.mouseButton, false)
  if (down !== null && up !== null) {
    parts.push(`
      [NauticalInput]::mouse_event(${down}, 0, 0, 0, [UIntPtr]::Zero)
      Start-Sleep -Milliseconds 12
      [NauticalInput]::mouse_event(${up}, 0, 0, 0, [UIntPtr]::Zero)
    `)
  }
  if (!parts.length) return
  const script = `
    Add-Type @"
      using System;
      using System.Runtime.InteropServices;
      public class NauticalInput {
        [DllImport("user32.dll")] public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);
        [DllImport("user32.dll")] public static extern void mouse_event(uint dwFlags, uint dx, uint dy, uint dwData, UIntPtr dwExtraInfo);
      }
"@
    ${parts.join('\n')}
  `
  await runPowerShell(script, 4000)
}

async function tick(): Promise<void> {
  if (!running || !current) return
  if (current.onlyWhileFortniteFocused || current.mode === 'fortnite-focus') {
    const focused = await isFortniteFocused()
    if (!focused) return
  }
  if (current.mode === 'hold' && !activationHeld) return
  if (current.mode === 'toggle' && !toggleOn) return
  try {
    await sendOnce(current)
  } catch {
    // Swallow send failures; next tick retries.
  }
}

export function isMacroRunning(): boolean {
  return running
}

export function setActivationHeld(held: boolean): void {
  activationHeld = held
}

export function toggleActivation(): void {
  toggleOn = !toggleOn
}

export function startMacro(settings: MacroSettings): OperationResult {
  if (!settings.acknowledgedRisk) {
    return { ok: false, code: 'MACRO_NOT_ACKNOWLEDGED', message: 'Acknowledge the Epic rules notice before enabling a macro.' }
  }
  if (!settings.enabled) {
    return { ok: false, message: 'Macro is disabled in the active profile.' }
  }
  if (!(ALLOWED_MACRO_KEYS as readonly string[]).includes(settings.key) && settings.mouseButton === 'none') {
    return { ok: false, message: 'Choose a supported key or mouse button.' }
  }
  const interval = Math.min(2, Math.max(0.1, settings.intervalSec))
  current = { ...settings, intervalSec: interval }
  if (timer) clearInterval(timer)
  running = true
  if (settings.mode === 'fortnite-focus') toggleOn = true
  timer = setInterval(() => {
    void tick()
  }, Math.round(interval * 1000))
  void tick()
  return {
    ok: true,
    message: isWindows
      ? `Macro armed (${settings.key}${settings.mouseButton !== 'none' ? ` + ${settings.mouseButton}` : ''} every ${interval.toFixed(2)}s).`
      : 'Macro armed in preview mode. Key sending is Windows-only.'
  }
}

export function stopMacro(): OperationResult {
  running = false
  toggleOn = false
  activationHeld = false
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  current = null
  return { ok: true, message: 'Macro stopped.' }
}

export function updateMacroRuntime(settings: MacroSettings): void {
  if (running) {
    startMacro(settings)
  } else {
    current = settings
  }
}
