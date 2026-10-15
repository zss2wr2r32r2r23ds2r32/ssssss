import { dialog, type BrowserWindow } from 'electron'
import { existsSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import type { AvatarSettings, OperationResult } from '../../shared/types'
import { configPath, loadConfig, updateConfig } from './config'

const ALLOWED = new Set(['.png', '.jpg', '.jpeg', '.webp'])

function avatarDir(): string {
  return path.dirname(configPath())
}

export function avatarFilePath(fileName: string | null = loadConfig().avatar.fileName): string | null {
  if (!fileName) return null
  const file = path.join(avatarDir(), path.basename(fileName))
  return existsSync(file) ? file : null
}

export function readAvatarDataUrl(): string | null {
  const config = loadConfig()
  const file = avatarFilePath(config.avatar.fileName)
  if (!file || !config.avatar.mime) return null
  const buffer = readFileSync(file)
  return `data:${config.avatar.mime};base64,${buffer.toString('base64')}`
}

export async function importAvatar(win: BrowserWindow | null): Promise<OperationResult<{ avatar: AvatarSettings; image: string }>> {
  const result = await (win
    ? dialog.showOpenDialog(win, {
        title: 'Choose a profile picture',
        properties: ['openFile'],
        filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp'] }]
      })
    : dialog.showOpenDialog({
        title: 'Choose a profile picture',
        properties: ['openFile'],
        filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp'] }]
      }))
  if (result.canceled || !result.filePaths[0]) {
    return { ok: false, message: 'Import cancelled.' }
  }
  const source = result.filePaths[0]
  const ext = path.extname(source).toLowerCase()
  if (!ALLOWED.has(ext)) {
    return { ok: false, message: 'Use a PNG, JPG, or WebP image.' }
  }
  const buffer = readFileSync(source)
  if (buffer.byteLength > 2_500_000) {
    return { ok: false, message: 'Image is larger than 2.5 MB.' }
  }
  const fileName = `profile-picture${ext === '.jpeg' ? '.jpg' : ext}`
  const dest = path.join(avatarDir(), fileName)
  writeFileSync(dest, buffer)
  const mime = ext === '.png' ? 'image/png' : ext === '.webp' ? 'image/webp' : 'image/jpeg'
  const avatar: AvatarSettings = { fileName, mime }
  updateConfig({ avatar })
  return {
    ok: true,
    message: 'Profile picture saved.',
    data: { avatar, image: `data:${mime};base64,${buffer.toString('base64')}` }
  }
}

export function clearAvatar(): AvatarSettings {
  const current = loadConfig().avatar
  const file = avatarFilePath(current.fileName)
  if (file) {
    try {
      unlinkSync(file)
    } catch {
      // Keep going.
    }
  }
  const avatar: AvatarSettings = { fileName: null, mime: null }
  updateConfig({ avatar })
  return avatar
}
