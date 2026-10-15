import { dialog } from 'electron'
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import type { BrowserWindow } from 'electron'
import type { OperationResult, SkinPreview } from '../../shared/types'
import { loadConfig, updateConfig } from './config'

function fortniteLogDir(): string | null {
  const local = process.env.LOCALAPPDATA
  if (!local) return null
  const dir = path.join(local, 'FortniteGame', 'Saved', 'Logs')
  return existsSync(dir) ? dir : null
}

const KNOWN_NAMES: Record<string, string> = {
  CID_001_Athena_Commando_F_Default: 'Default (Female)',
  CID_002_Athena_Commando_M_Default: 'Default (Male)',
  CID_028_Athena_Commando_F: 'Renegade Raider',
  CID_029_Athena_Commando_F_Halloween: 'Ghoul Trooper',
  CID_030_Athena_Commando_M_Halloween: 'Skull Trooper'
}

export function detectLastUsedSkin(): SkinPreview {
  const config = loadConfig()
  if (config.skin.image || (config.skin.name && config.skin.source === 'upload')) {
    return config.skin
  }
  const dir = fortniteLogDir()
  if (!dir) {
    return config.skin.source === 'log'
      ? config.skin
      : { name: null, image: null, source: 'placeholder', cosmeticId: null }
  }
  try {
    const logs = readdirSync(dir)
      .filter((file) => file.toLowerCase().endsWith('.log'))
      .map((file) => path.join(dir, file))
      .sort((a, b) => {
        try {
          return readFileSync(b).length - readFileSync(a).length
        } catch {
          return 0
        }
      })
      .slice(0, 4)
    let last: string | null = null
    for (const file of logs) {
      const text = readFileSync(file, 'utf8')
      const matches = [...text.matchAll(/\b(CID_[A-Za-z0-9_]+)\b/g)].map((m) => m[1])
      if (matches.length) last = matches[matches.length - 1]
    }
    if (!last) {
      return { name: null, image: null, source: 'placeholder', cosmeticId: null }
    }
    const skin: SkinPreview = {
      name: KNOWN_NAMES[last] ?? last.replace(/^CID_/, '').replace(/_/g, ' '),
      image: null,
      source: 'log',
      cosmeticId: last
    }
    updateConfig({ skin })
    return skin
  } catch {
    return { name: null, image: null, source: 'placeholder', cosmeticId: null }
  }
}

export async function importSkinPreview(win: BrowserWindow | null): Promise<OperationResult<SkinPreview>> {
  const result = await (win
    ? dialog.showOpenDialog(win, {
        title: 'Choose a skin preview image',
        properties: ['openFile'],
        filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp'] }]
      })
    : dialog.showOpenDialog({
        title: 'Choose a skin preview image',
        properties: ['openFile'],
        filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp'] }]
      }))
  if (result.canceled || !result.filePaths[0]) {
    return { ok: false, message: 'Import cancelled.' }
  }
  const filePath = result.filePaths[0]
  const buffer = readFileSync(filePath)
  if (buffer.byteLength > 2_000_000) {
    return { ok: false, message: 'Image is larger than 2 MB.' }
  }
  const ext = path.extname(filePath).toLowerCase()
  const mime = ext === '.jpg' || ext === '.jpeg' ? 'image/jpeg' : ext === '.webp' ? 'image/webp' : 'image/png'
  const skin: SkinPreview = {
    name: path.basename(filePath, ext),
    image: `data:${mime};base64,${buffer.toString('base64')}`,
    source: 'upload',
    cosmeticId: loadConfig().skin.cosmeticId
  }
  updateConfig({ skin })
  return { ok: true, message: 'Skin preview saved.', data: skin }
}
