import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { buildCrosshair } from '../src/shared/crosshair-draw'
import { createDefaultConfig, getActiveProfile, seedProfiles, seedScrims } from '../src/shared/defaults'
import { isAllowedExternalUrl, isDiscordWebhookUrl, isIpcChannel } from '../src/shared/ipc'
import { APP_NAME, CROSSHAIR_PRESETS, RESOLUTION_PRESETS } from '../src/shared/types'
import {
  collectHintsFromManifestText,
  executablesForInstall,
  isAllowedFortniteExecutableName,
  isDebugPreviewMessage,
  looksLikeFortniteName,
  rankFortniteCandidates
} from '../src/shared/fortnite-detect'
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
    expect(APP_NAME).toBe('Avix Launcher')
    expect(config.appearance.accent).toBe('#FF4D9D')
    expect(config.general.closeToTray).toBe(false)
    expect(config.skin.source).toBe('placeholder')
    expect(config.scrims.sources.map((s) => s.name)).toEqual([
      'Noble Elite',
      'Poyo Elite',
      'Ladder Elite',
      'Duos Elite',
      'Solos Elite'
    ])
    expect(getActiveProfile(config).macro.mouseButton).toBe('none')
  })
})

describe('scrim + webhook helpers', () => {
  it('seeds the default elite formats', () => {
    expect(seedScrims().sources).toHaveLength(5)
  })

  it('accepts only Discord webhook URLs', () => {
    expect(isDiscordWebhookUrl('https://discord.com/api/webhooks/123/abc-token')).toBe(true)
    expect(isDiscordWebhookUrl('https://discordapp.com/api/webhooks/123/abc_token')).toBe(true)
    expect(isDiscordWebhookUrl('https://discord.com/invite/fortnite')).toBe(false)
    expect(isDiscordWebhookUrl('https://evil.example/api/webhooks/1/x')).toBe(false)
  })
})

describe('fortnite path validation', () => {
  it('rejects non-exe files', () => {
    expect(looksLikeFortniteExecutable('C:\\Games\\readme.txt').valid).toBe(false)
  })

  it('rejects unrelated executables', () => {
    expect(looksLikeFortniteExecutable('C:\\Windows\\notepad.exe').valid).toBe(false)
  })

  it('accepts shipping, bootstrapper, and Fortnite.exe names', () => {
    const names = [
      'C:\\Program Files\\Epic Games\\Fortnite\\FortniteGame\\Binaries\\Win64\\FortniteClient-Win64-Shipping.exe',
      'C:\\Program Files\\Epic Games\\Fortnite\\FortniteGame\\Binaries\\Win64\\FortniteBootstrapper.exe',
      'C:\\Program Files\\Epic Games\\Fortnite\\Fortnite.exe'
    ]
    for (const file of names) {
      expect(isAllowedFortniteExecutableName(file)).toBe(true)
      expect(looksLikeFortniteName(file).valid).toBe(true)
    }
  })

  it('accepts a Fortnite-named file that exists', () => {
    const dir = mkdtempSync(join(tmpdir(), 'nautical-'))
    const file = join(dir, 'FortniteClient-Win64-Shipping.exe')
    writeFileSync(file, 'mz')
    expect(looksLikeFortniteExecutable(file).valid).toBe(true)
    const bootstrapper = join(dir, 'FortniteBootstrapper.exe')
    writeFileSync(bootstrapper, 'mz')
    expect(looksLikeFortniteExecutable(bootstrapper).valid).toBe(true)
  })
})

describe('epic manifest resolution', () => {
  it('parses Fortnite .item InstallLocation + LaunchExecutable', () => {
    const raw = JSON.stringify({
      AppName: 'Fortnite',
      DisplayName: 'Fortnite',
      InstallLocation: 'C:\\Program Files\\Epic Games\\Fortnite',
      LaunchExecutable: 'FortniteGame/Binaries/Win64/FortniteBootstrapper.exe'
    })
    const hints = collectHintsFromManifestText(raw)
    expect(hints).toHaveLength(1)
    expect(hints[0].installLocation).toBe('C:\\Program Files\\Epic Games\\Fortnite')
    const candidates = rankFortniteCandidates(executablesForInstall(hints[0].installLocation, hints[0].launchExecutable))
    expect(candidates[0]).toBe(
      'C:\\Program Files\\Epic Games\\Fortnite\\FortniteGame\\Binaries\\Win64\\FortniteClient-Win64-Shipping.exe'
    )
    expect(candidates).toContain(
      'C:\\Program Files\\Epic Games\\Fortnite\\FortniteGame\\Binaries\\Win64\\FortniteBootstrapper.exe'
    )
  })

  it('parses LauncherInstalled.dat InstallationList', () => {
    const raw = JSON.stringify({
      InstallationList: [
        { InstallLocation: 'C:\\Games\\Other', AppName: 'Celeste' },
        { InstallLocation: 'C:\\Program Files\\Epic Games\\Fortnite', AppName: 'Fortnite' }
      ]
    })
    const hints = collectHintsFromManifestText(raw)
    expect(hints.map((h) => h.appName)).toEqual(['Fortnite'])
  })

  it('blocks leftover preview debug toast copy', () => {
    expect(isDebugPreviewMessage('Browser preview: detect on Windows from the packaged app.')).toBe(true)
    expect(isDebugPreviewMessage('Fortnite was not found in Epic manifests.')).toBe(false)
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
    expect(isIpcChannel('window:maximize')).toBe(true)
    expect(isIpcChannel('window:close')).toBe(true)
    expect(isIpcChannel('skin:detect')).toBe(true)
    expect(isIpcChannel('scrims:test')).toBe(true)
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
