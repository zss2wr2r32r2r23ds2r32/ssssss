import { net, shell } from 'electron'
import { APP_VERSION } from '../../shared/types'
import {
  compareVersions,
  GITHUB_LATEST_RELEASE_API,
  GITHUB_RELEASES_URL,
  GITHUB_TAGS_API
} from '../../shared/fortnite-launch'

export interface UpdateCheck {
  ok: boolean
  current: string
  latest: string | null
  newer: boolean
  downloadUrl: string | null
  releasesUrl: string
  message: string
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

export async function checkForUpdates(): Promise<UpdateCheck> {
  const current = APP_VERSION
  const base: UpdateCheck = {
    ok: true,
    current,
    latest: null,
    newer: false,
    downloadUrl: null,
    releasesUrl: GITHUB_RELEASES_URL,
    message: `You are running Avix ${current}.`
  }
  try {
    const latestRelease = await requestJson(GITHUB_LATEST_RELEASE_API)
    if (latestRelease.status === 200) {
      const parsed = JSON.parse(latestRelease.body) as {
        tag_name?: string
        html_url?: string
        assets?: Array<{ name?: string; browser_download_url?: string }>
      }
      const latest = (parsed.tag_name ?? '').replace(/^v/i, '')
      const portable = parsed.assets?.find((asset) => /portable/i.test(asset.name ?? ''))
      const downloadUrl = portable?.browser_download_url ?? parsed.html_url ?? GITHUB_RELEASES_URL
      const newer = latest ? compareVersions(current, latest) > 0 : false
      return {
        ...base,
        latest: latest || null,
        newer,
        downloadUrl,
        message: newer
          ? `Avix ${latest} is available. Open the download page to get the latest portable/installer.`
          : latest
            ? `You are on Avix ${current}. Latest GitHub release is ${latest}.`
            : base.message
      }
    }
    const tags = await requestJson(GITHUB_TAGS_API)
    if (tags.status === 200) {
      const parsed = JSON.parse(tags.body) as Array<{ name?: string }>
      const latest = (parsed[0]?.name ?? '').replace(/^v/i, '')
      const newer = latest ? compareVersions(current, latest) > 0 : false
      return {
        ...base,
        latest: latest || null,
        newer,
        downloadUrl: GITHUB_RELEASES_URL,
        message: latest
          ? newer
            ? `Newer tag ${latest} exists. No GitHub release assets yet — open the releases page.`
            : `You are on Avix ${current}. Latest tag is ${latest}.`
          : 'No GitHub releases or tags yet. You are running Avix 1.0.0.'
      }
    }
    return {
      ...base,
      message: 'No GitHub release is published yet. You are running Avix 1.0.0. The Check button will pick up a release when one exists.'
    }
  } catch (error) {
    return {
      ...base,
      ok: false,
      message: error instanceof Error ? `Could not reach GitHub: ${error.message}` : 'Could not reach GitHub releases.'
    }
  }
}

export async function openReleasesPage(url = GITHUB_RELEASES_URL): Promise<void> {
  await shell.openExternal(url)
}
