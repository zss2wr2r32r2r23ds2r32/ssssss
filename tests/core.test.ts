import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { buildCrosshair } from '../src/shared/crosshair-draw'
import { createDefaultConfig, getActiveProfile, seedProfiles } from '../src/shared/defaults'
import { isAllowedExternalUrl, isIpcChannel } from '../src/shared/ipc'
import { CROSSHAIR_PRESETS, RESOLUTION_PRESETS } from '../src/shared/types'
import { looksLikeFortniteExecutable } from '../src/main/services/windows-api'
import { isProtectedProcess } from '../src/main/services/performance'

describe('profiles', () => {
  it('seeds Competitive, Aggressive, and Native', () => {
    const names = seedProfiles().map((p) => p.name)
    expect(names).toEqual(['Competitive', 'Aggressive', 'Native'])
  })

  it('createDefaultConfig points at Competitive', () => {
    const config = createDefaultConfig()
    expect(getActiveProfile(config).name).toBe('Competitive')
    expect(config.version).toBe('1.0.0')
    expect(config.wizardCompleted).toBe(false)
  })
})

describe('fortnite path validation', () => {
  it('rejects non-exe files', () => {
    expect(looksLikeFortniteExecutable('C:\\Games\\readme.txt').valid).toBe(false)
  })

  it('rejects unrelated executables', () => {
    expect(looksLikeFortniteExecutable('C:\\Windows\\notepad.exe').valid).toBe(false)
  })

  it('accepts a Fortnite-named file that exists', () => {
    const dir = mkdtempSync(join(tmpdir(), 'nautical-'))
    const file = join(dir, 'FortniteClient-Win64-Shipping.exe')
    writeFileSync(file, 'mz')
    expect(looksLikeFortniteExecutable(file).valid).toBe(true)
  })
})

describe('cleanup whitelist', () => {
  it('protects system, GPU, AV, Fortnite, and Epic auth', () => {
    for (const name of ['csrss', 'lsass', 'explorer', 'nvcontainer', 'MsMpEng', 'FortniteClient-Win64-Shipping', 'EpicGamesLauncher', 'EasyAntiCheat']) {
      expect(isProtectedProcess(name)).toBe(true)
    }
  })

  it('allows optional chat/browser candidates', () => {
    expect(isProtectedProcess('Discord')).toBe(false)
    expect(isProtectedProcess('chrome')).toBe(false)
    expect(isProtectedProcess('Spotify')).toBe(false)
  })
})

describe('security helpers', () => {
  it('only allows Discord HTTPS hosts', () => {
    expect(isAllowedExternalUrl('https://discord.gg/fortnite')).toBe(true)
    expect(isAllowedExternalUrl('https://discord.com/invite/x')).toBe(true)
    expect(isAllowedExternalUrl('https://evil.example/steal')).toBe(false)
    expect(isAllowedExternalUrl('file:///etc/passwd')).toBe(false)
  })

  it('whitelists IPC channels', () => {
    expect(isIpcChannel('fortnite:launch')).toBe(true)
    expect(isIpcChannel('fs:write')).toBe(false)
  })
})

describe('crosshair + resolution catalogs', () => {
  it('has the required crosshair presets', () => {
    expect(CROSSHAIR_PRESETS.map((p) => p.name)).toEqual([
      'Classic Dot',
      'Small Cross',
      'Hollow Cross',
      'T Cross',
      'Plus',
      'Precision Dot',
      'Four Lines',
      'Circle',
      'Dot + Circle',
      'Minimal'
    ])
  })

  it('builds drawable geometry', () => {
    const config = createDefaultConfig()
    const geo = buildCrosshair(getActiveProfile(config).crosshair)
    expect(geo.dots.length + geo.lines.length + geo.circles.length).toBeGreaterThan(0)
  })

  it('includes example resolutions without claiming they are best', () => {
    expect(RESOLUTION_PRESETS).toHaveLength(6)
    expect(RESOLUTION_PRESETS[0]).toMatchObject({ width: 1920, height: 1080 })
  })
})
