import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';

function readVersion() {
  if (process.env.NEXA_APP_VERSION) return process.env.NEXA_APP_VERSION;
  const candidates = [path.join(process.cwd(), 'package.json'), path.join(process.cwd(), '..', 'package.json')];
  for (const file of candidates) {
    try {
      const parsed = JSON.parse(fs.readFileSync(file, 'utf8'));
      if (parsed.name === 'nexa' && parsed.version) return parsed.version;
    } catch {
      /* try the next package.json */
    }
  }
  return '0.1.2';
}

export const APP_VERSION = readVersion();

const REPO = 'zss2wr2r32r2r23ds2r32/ssssss';
const TAG = /^nexa-(\d+)\.(\d+)\.(\d+)$/;

export function parseReleaseTag(tag) {
  const match = TAG.exec(String(tag || '').trim());
  if (!match) return null;
  return `${match[1]}.${match[2]}.${match[3]}`;
}

export function compareVersions(left, right) {
  const a = String(left).split('.').map((part) => Number(part));
  const b = String(right).split('.').map((part) => Number(part));
  for (let index = 0; index < 3; index += 1) {
    const delta = (a[index] || 0) - (b[index] || 0);
    if (delta !== 0) return delta;
  }
  return 0;
}

async function githubReleases(fetchImpl) {
  const response = await fetchImpl(`https://api.github.com/repos/${REPO}/releases?per_page=20`, {
    headers: {
      Accept: 'application/vnd.github+json',
      'User-Agent': 'nexa-launcher',
    },
  });
  if (!response.ok) throw new Error('Could not reach GitHub releases.');
  const releases = await response.json();
  if (!Array.isArray(releases)) throw new Error('GitHub did not return a release list.');
  return releases;
}

export async function latestRelease(fetchImpl = fetch) {
  const releases = await githubReleases(fetchImpl);
  let best = null;
  for (const release of releases) {
    const version = parseReleaseTag(release.tag_name);
    if (!version) continue;
    if (best && compareVersions(version, best.version) <= 0) continue;
    const asset = (release.assets || []).find((item) => item.name === 'Nexa.exe');
    best = {
      version,
      tag: String(release.tag_name),
      downloadUrl: asset?.browser_download_url || '',
    };
  }
  if (!best) throw new Error('No release tagged nexa-x.y.z was found.');
  return best;
}

export async function updateStatus(fetchImpl = fetch) {
  const latest = await latestRelease(fetchImpl);
  const updateAvailable = compareVersions(latest.version, APP_VERSION) > 0;
  return {
    current: APP_VERSION,
    latest: latest.version,
    tag: latest.tag,
    updateAvailable,
    downloadUrl: latest.downloadUrl,
    message: updateAvailable ? `Version ${latest.version} is available.` : "You're on the latest version.",
  };
}

function quitApp() {
  setTimeout(() => {
    if (typeof global.__nexaExit === 'function') {
      global.__nexaExit();
      return;
    }
    process.exit(0);
  }, 400);
}

export async function applyUpdate(fetchImpl = fetch) {
  const status = await updateStatus(fetchImpl);
  if (!status.updateAvailable) return status;
  if (!status.downloadUrl) throw new Error('The latest release has no Nexa.exe asset.');
  const packaged = Boolean(process.versions.electron && process.platform === 'win32');
  if (!packaged) {
    return {
      ...status,
      message: `Version ${status.latest} is available. Check for updates again from Nexa.exe to install it.`,
    };
  }

  const destination = process.env.PORTABLE_EXECUTABLE_FILE || process.execPath;
  const folder = path.join(os.tmpdir(), 'nexa-update');
  fs.mkdirSync(folder, { recursive: true });
  const downloaded = path.join(folder, 'Nexa.exe');
  const response = await fetchImpl(status.downloadUrl, {
    headers: { 'User-Agent': 'nexa-launcher', Accept: 'application/octet-stream' },
  });
  if (!response.ok || !response.body) throw new Error('Could not download the update.');
  await pipeline(Readable.fromWeb(response.body), fs.createWriteStream(downloaded));

  const script = path.join(folder, 'replace.bat');
  const bat = [
    '@echo off',
    'set PID=%1',
    'set SRC=%~2',
    'set DEST=%~3',
    ':wait',
    'tasklist /FI "PID eq %PID%" | find "%PID%" >nul',
    'if not errorlevel 1 (',
    '  timeout /t 1 /nobreak >nul',
    '  goto wait',
    ')',
    'copy /Y "%SRC%" "%DEST%"',
    'start "" "%DEST%"',
    'del "%~f0"',
    '',
  ].join('\r\n');
  fs.writeFileSync(script, bat);
  const child = spawn('cmd.exe', ['/c', script, String(process.pid), downloaded, destination], {
    detached: true,
    stdio: 'ignore',
    windowsHide: true,
  });
  child.unref();
  quitApp();
  return { ...status, message: `Updating to ${status.latest}. Nexa will restart.` };
}
