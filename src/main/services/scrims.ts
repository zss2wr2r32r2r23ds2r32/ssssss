import { net } from 'electron'
import { createId } from '../../shared/defaults'
import { isDiscordWebhookUrl } from '../../shared/ipc'
import type { OperationResult, ScrimSettings, ScrimSource } from '../../shared/types'
import { loadConfig, updateConfig } from './config'

let pollTimer: NodeJS.Timeout | null = null
const listeners = new Set<(title: string, body: string) => void>()

export function onScrimAlert(listener: (title: string, body: string) => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function emit(title: string, body: string): void {
  for (const listener of listeners) listener(title, body)
}

export async function sendDiscordWebhook(message: string): Promise<OperationResult> {
  const url = loadConfig().general.discordWebhookUrl.trim()
  if (!url) return { ok: false, message: 'No Discord webhook configured.' }
  if (!isDiscordWebhookUrl(url)) {
    return { ok: false, message: 'Webhook URL must be an https://discord.com/api/webhooks/… address.' }
  }
  try {
    await new Promise<void>((resolve, reject) => {
      const request = net.request({ method: 'POST', url })
      request.setHeader('Content-Type', 'application/json')
      request.on('response', (response) => {
        if (response.statusCode && response.statusCode >= 400) {
          reject(new Error(`Discord webhook returned ${response.statusCode}`))
        } else {
          resolve()
        }
      })
      request.on('error', reject)
      request.write(JSON.stringify({ content: message }))
      request.end()
    })
    return { ok: true, message: 'Webhook sent.' }
  } catch (error) {
    return { ok: false, message: error instanceof Error ? error.message : 'Webhook failed.' }
  }
}

export async function fireScrimAlert(source: ScrimSource): Promise<void> {
  const title = 'Scrim alert'
  const body = `Scrim is on — ${source.name}`
  emit(title, body)
  const webhook = loadConfig().general.discordWebhookUrl.trim()
  if (webhook) {
    await sendDiscordWebhook(body)
  }
  const config = loadConfig()
  updateConfig({
    scrims: {
      ...config.scrims,
      sources: config.scrims.sources.map((item) =>
        item.id === source.id ? { ...item, lastLiveAt: new Date().toISOString() } : item
      )
    }
  })
}

async function fetchStatus(url: string): Promise<boolean> {
  return await new Promise((resolve) => {
    try {
      const request = net.request({ method: 'GET', url })
      let raw = ''
      request.on('response', (response) => {
        response.on('data', (chunk) => {
          raw += chunk.toString()
        })
        response.on('end', () => {
          try {
            const parsed = JSON.parse(raw) as { live?: boolean; status?: string }
            if (typeof parsed.live === 'boolean') {
              resolve(parsed.live)
              return
            }
          } catch {
            // text fallback
          }
          resolve(/\blive\b/i.test(raw) && !/\bnot live\b/i.test(raw))
        })
      })
      request.on('error', () => resolve(false))
      request.end()
    } catch {
      resolve(false)
    }
  })
}

export async function refreshScrims(): Promise<ScrimSettings> {
  const config = loadConfig()
  for (const source of config.scrims.sources) {
    if (!source.enabled || !source.statusUrl) continue
    try {
      const parsed = new URL(source.statusUrl)
      if (parsed.protocol !== 'https:') continue
      const live = await fetchStatus(source.statusUrl)
      if (live) {
        const recently =
          source.lastLiveAt && Date.now() - new Date(source.lastLiveAt).getTime() < config.scrims.pollSeconds * 2000
        if (!recently) await fireScrimAlert(source)
      }
    } catch {
      // Ignore a single bad source.
    }
  }
  return loadConfig().scrims
}

export function startScrimPoller(): void {
  stopScrimPoller()
  const config = loadConfig()
  const active = config.scrims.sources.some((source) => source.enabled && source.statusUrl)
  if (!active) return
  const tick = () => {
    void refreshScrims()
  }
  const seconds = Math.min(600, Math.max(20, config.scrims.pollSeconds || 60))
  pollTimer = setInterval(tick, seconds * 1000)
}

export function stopScrimPoller(): void {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

export function updateScrims(next: ScrimSettings): ScrimSettings {
  const cleaned: ScrimSettings = {
    pollSeconds: Math.min(600, Math.max(20, next.pollSeconds || 60)),
    sources: next.sources.slice(0, 24).map((source) => ({
      id: source.id || createId('scrim'),
      name: source.name.slice(0, 48),
      enabled: Boolean(source.enabled),
      statusUrl: source.statusUrl && source.statusUrl.startsWith('https://') ? source.statusUrl : null,
      lastLiveAt: source.lastLiveAt ?? null
    }))
  }
  updateConfig({ scrims: cleaned })
  startScrimPoller()
  return cleaned
}
