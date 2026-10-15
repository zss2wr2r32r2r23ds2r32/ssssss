import { BrowserWindow, app, dialog, net, shell } from 'electron'
import { spawn } from 'node:child_process'
import { autoUpdater } from 'electron-updater'
import { APP_VERSION } from '../../shared/types'
import type { OperationResult, UpdateCheck } from '../../shared/types'
import {
  compareVersions,
  GITHUB_LATEST_RELEASE_API,
  GITHUB_RELEASES_URL,
  GITHUB_TAGS_API
} from '../../shared/fortnite-launch'

let state: UpdateCheck = {
  ok: true,
  current: APP_VERSION,
  latest: null,
  newer: false,
  downloadUrl: null,
  releasesUrl: GITHUB_RELEASES_URL,
  message: `Avix ${APP_VERSION}`,
  state: 'idle',
  downloaded: false,
  progress: null
}

let configured = false

function snapshot(patch: Partial<UpdateCheck> = {}): UpdateCheck {
  state = { ...state, current: APP_VERSION, releasesUrl: GITHUB_RELEASES_URL, ...patch }
  return { ...state }
}

export function getUpdateStatus(): UpdateCheck {
  return { ...state, current: APP_VERSION }
}

export function setupAutoUpdater(): void {
  if (configured) return
  configured = true
  autoUpdater.autoDownload = false
  autoUpdater.autoInstallOnAppQuit = true
  autoUpdater.allowDowngrade = false
  autoUpdater.on('checking-for-update', () => {
    snapshot({ state: 'checking', message: `Checking GitHub releases for Avix ${APP_VERSION}…` })
  })
  autoUpdater.on('update-available', (info) => {
    snapshot({
      ok: true,
      newer: true,
      latest: info.version,
      state: 'available',
      message: `Avix ${info.version} is available. Download it, then Install and restart.`
    })
  })
  autoUpdater.on('update-not-available', () => {
    snapshot({
      ok: true,
      newer: false,
      state: 'none',
      message: `You are on Avix ${APP_VERSION}. No newer GitHub release is installed yet.`
    })
  })
  autoUpdater.on('download-progress', (progress) => {
    snapshot({
      state: 'downloading',
      progress: Math.round(progress.percent),
      message: `Downloading update… ${Math.round(progress.percent)}%`
    })
  })
  autoUpdater.on('update-downloaded', (info) => {
    snapshot({
      newer: true,
      latest: info.version,
      downloaded: true,
      progress: 100,
      state: 'ready',
      message: `Avix ${info.version} is downloaded. Install and restart to apply it.`
    })
  })
  autoUpdater.on('error', (error) => {
    snapshot({
      ok: false,
      state: 'error',
      message: error instanceof Error ? error.message : 'Updater error'
    })
  })
}

function requestJson(url: string): Promise<{ status: number; body: string }> {
  return new Promise((resolve, reject) => {
    const request = net.request({ method: 'GET', url })
    request.setHeader('User-Agent', 'Avix-Launcher')
    request.setHeader('Accept', 'application/vnd.github+json')
    let body = ''
    request.on('response', (response) => {
      response.on('data', (chunk) => {
        body += chunk.toString()
      })
      response.on('end', () => resolve({ status: response.statusCode ?? 0, body }))
    })
    request.on('error', reject)
    request.end()
  })
}

async function githubProbe(): Promise<UpdateCheck> {
  const current = APP_VERSION
  try {
    const latestRelease = await requestJson(GITHUB_LATEST_RELEASE_API)
    if (latestRelease.status === 200) {
      const parsed = JSON.parse(latestRelease.body) as {
        tag_name?: string
        html_url?: string
        assets?: Array<{ name?: string; browser_download_url?: string }>
      }
      const latest = (parsed.tag_name ?? '').replace(/^v/i, '')
      const portable = parsed.assets?.find((asset) => /portable|nsis|setup|\.exe/i.test(asset.name ?? ''))
      const downloadUrl = portable?.browser_download_url ?? parsed.html_url ?? GITHUB_RELEASES_URL
      const newer = latest ? compareVersions(current, latest) > 0 : false
      return snapshot({
        ok: true,
        latest: latest || null,
        newer,
        downloadUrl,
        state: newer ? 'available' : 'none',
        message: newer
          ? `Avix ${latest} is on GitHub. Use Download, then Install and restart — or Install from file.`
          : latest
            ? `You are on Avix ${current}. Latest GitHub release is ${latest}.`
            : `You are running Avix ${current}.`
      })
    }
    const tags = await requestJson(GITHUB_TAGS_API)
    if (tags.status === 200) {
      const parsed = JSON.parse(tags.body) as Array<{ name?: string }>
      const latest = (parsed[0]?.name ?? '').replace(/^v/i, '')
      const newer = latest ? compareVersions(current, latest) > 0 : false
      return snapshot({
        ok: true,
        latest: latest || null,
        newer,
        downloadUrl: GITHUB_RELEASES_URL,
        state: newer ? 'available' : 'none',
        message: latest
          ? newer
            ? `Newer tag ${latest} exists, but no GitHub Release assets yet. Create a Release on GitHub, or use Install from file.`
            : `You are on Avix ${current}. Latest tag is ${latest}.`
          : `No GitHub releases yet. You are running Avix ${current}. Create a Release at ${GITHUB_RELEASES_URL} so in-app install can download.`
      })
    }
    return snapshot({
      ok: true,
      state: 'none',
      message: `No GitHub release is published yet. You are running Avix ${current}. Publish a Release on GitHub, or use Install from file.`
    })
  } catch (error) {
    return snapshot({
      ok: false,
      state: 'error',
      message: error instanceof Error ? `Could not reach GitHub: ${error.message}` : 'Could not reach GitHub releases.'
    })
  }
}

export async function checkForUpdates(): Promise<UpdateCheck> {
  setupAutoUpdater()
  snapshot({ state: 'checking', message: `Checking for Avix updates…` })
  if (app.isPackaged) {
    try {
      const result = await autoUpdater.checkForUpdates()
      const version = result?.updateInfo?.version
      if (version && compareVersions(APP_VERSION, version) > 0) {
        return snapshot({
          ok: true,
          latest: version,
          newer: true,
          state: 'available',
          downloadUrl: GITHUB_RELEASES_URL,
          message: `Avix ${version} is available. Download it, then Install and restart.`
        })
      }
    } catch {
      // Fall through to the GitHub API probe (no release, or not packaged metadata).
    }
  }
  return githubProbe()
}

export async function downloadUpdate(): Promise<UpdateCheck> {
  setupAutoUpdater()
  if (!app.isPackaged) {
    return snapshot({
      ok: false,
      state: 'error',
      message: 'In-app download works from the packaged Avix build. Use Install from file with a downloaded installer, or open the releases page.'
    })
  }
  try {
    snapshot({ state: 'downloading', progress: 0, message: 'Downloading update…' })
    await autoUpdater.downloadUpdate()
    return getUpdateStatus()
  } catch (error) {
    return snapshot({
      ok: false,
      state: 'error',
      message: error instanceof Error ? error.message : 'Download failed. Use Install from file.'
    })
  }
}

export async function installDownloadedUpdate(): Promise<OperationResult> {
  setupAutoUpdater()
  if (!state.downloaded && state.state !== 'ready') {
    return {
      ok: false,
      message: 'No update is downloaded yet. Check for updates, download, or use Install from file.'
    }
  }
  try {
    autoUpdater.quitAndInstall(false, true)
    return { ok: true, message: 'Installing and restarting Avix…' }
  } catch (error) {
    return { ok: false, message: error instanceof Error ? error.message : 'Install failed.' }
  }
}

export async function installFromFile(win: BrowserWindow | null): Promise<OperationResult> {
  const result = await (win
    ? dialog.showOpenDialog(win, {
        title: 'Install Avix from a downloaded installer',
        properties: ['openFile'],
        filters: [{ name: 'Avix installer / portable', extensions: ['exe'] }]
      })
    : dialog.showOpenDialog({
        title: 'Install Avix from a downloaded installer',
        properties: ['openFile'],
        filters: [{ name: 'Avix installer / portable', extensions: ['exe'] }]
      }))
  if (result.canceled || !result.filePaths[0]) {
    return { ok: false, message: 'Install from file cancelled.' }
  }
  const file = result.filePaths[0]
  spawn(file, [], { detached: true, stdio: 'ignore' }).unref()
  setTimeout(() => {
    app.quit()
  }, 400)
  return { ok: true, message: 'Started the downloaded Avix installer. This copy will close.' }
}

export async function openReleasesPage(url = GITHUB_RELEASES_URL): Promise<void> {
  await shell.openExternal(url)
}
