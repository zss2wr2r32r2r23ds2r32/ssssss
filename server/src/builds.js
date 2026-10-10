import fs from 'node:fs';
import path from 'node:path';

const SKIP_DIRS = new Set(['node_modules', '.git', 'dist']);
const SKIP_EXT = new Set(['.txt', '.json', '.md', '.png', '.jpg', '.jpeg', '.svg', '.gif', '.css', '.map', '.html']);

function walk(dir, depth, acc) {
  if (depth < 0 || acc.files.length > 400) return;
  let entries = [];
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const entry of entries) {
    if (entry.name.startsWith('.')) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (SKIP_DIRS.has(entry.name)) continue;
      walk(full, depth - 1, acc);
      continue;
    }
    if (!entry.isFile()) continue;
    acc.files.push(full);
  }
}

function readVersionFile(dir) {
  for (const name of ['version.txt', 'VERSION']) {
    const full = path.join(dir, name);
    try {
      const text = fs.readFileSync(full, 'utf8').slice(0, 200).trim().split(/\r?\n/)[0]?.trim();
      if (text) return text.slice(0, 48);
    } catch {
      /* missing */
    }
  }
  return null;
}

function versionFromText(text) {
  const season = text.match(/chapter\s*\d+\s*season\s*\d+/i);
  if (season) return season[0].replace(/\s+/g, ' ');
  const numeric = text.match(/\b\d+\.\d+(?:\.\d+)?\b/);
  return numeric ? numeric[0] : null;
}

function looksExecutable(file) {
  const ext = path.extname(file).toLowerCase();
  if (ext === '.exe' || ext === '.appimage') return true;
  if (SKIP_EXT.has(ext) || ext === '.sh' || ext === '.py' || ext === '.js') return false;
  try {
    fs.accessSync(file, fs.constants.X_OK);
    const stat = fs.statSync(file);
    return stat.isFile() && stat.size > 0 && stat.size < 1024 * 1024 * 1024;
  } catch {
    return false;
  }
}

function nearestTo(exe, paths) {
  if (!paths.length) return null;
  const exeDir = path.dirname(exe);
  return [...paths].sort((a, b) => {
    const depth = (file) => path.relative(exeDir, file).split(path.sep).length;
    return depth(a) - depth(b);
  })[0];
}

function buildTitle(exe) {
  const banned = new Set(['binaries', 'win64', 'win32', 'engine', 'fortnitegame', 'shipping']);
  const parts = path.dirname(exe).split(path.sep).filter(Boolean);
  for (let index = parts.length - 1; index >= 0; index -= 1) {
    if (!banned.has(parts[index].toLowerCase())) return parts[index].slice(0, 48);
  }
  return 'Fortnite';
}

export function findShippingBuild(folderPath) {
  if (!folderPath || typeof folderPath !== 'string') return { error: 'Choose a folder.' };
  const resolved = path.resolve(folderPath.trim());
  if (!fs.existsSync(resolved)) return { error: 'That folder is not on this machine.' };
  let root = resolved;
  try {
    const stat = fs.statSync(resolved);
    if (stat.isFile()) root = path.dirname(resolved);
    else if (!stat.isDirectory()) return { error: 'Choose a folder.' };
  } catch {
    return { error: 'That folder is not on this machine.' };
  }

  const exes = [];
  const splashes = [];
  const stack = [root];
  let seen = 0;
  while (stack.length && seen < 8000) {
    const dir = stack.pop();
    let entries = [];
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const entry of entries) {
      if (entry.name.startsWith('.')) continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        if (SKIP_DIRS.has(entry.name)) continue;
        stack.push(full);
        continue;
      }
      if (!entry.isFile()) continue;
      seen += 1;
      const lower = entry.name.toLowerCase();
      if (lower === 'fortniteshipping.exe') exes.push(full);
      if (lower === 'splash.bmp') splashes.push(full);
    }
  }

  if (!exes.length) return { error: "Couldn't find FortniteShipping.exe in that folder." };
  const executablePath = exes[0];
  const splashPath = nearestTo(executablePath, splashes);
  const name = buildTitle(executablePath);
  const version = versionFromText(name) || versionFromText(path.basename(root)) || 'Local';
  return { folderPath: root, executablePath, splashPath, name, version };
}

export function inspectFolder(folderPath) {
  const empty = { exists: false, version: null, executablePath: null };
  if (!folderPath || typeof folderPath !== 'string') return empty;
  const resolved = path.resolve(folderPath.trim());
  if (!fs.existsSync(resolved)) return empty;

  let dir = resolved;
  let directFile = null;
  try {
    const stat = fs.statSync(resolved);
    if (stat.isFile()) {
      directFile = resolved;
      dir = path.dirname(resolved);
    } else if (!stat.isDirectory()) {
      return empty;
    }
  } catch {
    return empty;
  }

  const version = readVersionFile(dir) || versionFromText(path.basename(dir)) || versionFromText(resolved);
  const acc = { files: [] };
  if (!directFile) walk(dir, 2, acc);

  let executablePath = null;
  if (directFile && looksExecutable(directFile)) executablePath = directFile;
  if (!executablePath) {
    const exe = acc.files.find((file) => path.extname(file).toLowerCase() === '.exe' || path.extname(file).toLowerCase() === '.appimage');
    executablePath = exe || acc.files.find((file) => looksExecutable(file)) || null;
  }

  let detected = version;
  if (!detected) {
    for (const file of acc.files) {
      detected = versionFromText(path.basename(file));
      if (detected) break;
    }
  }

  return { exists: true, version: detected, executablePath };
}
