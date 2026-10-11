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

const SHIPPING_EXE = 'fortniteclient-win64-shipping.exe';

function findNamedDir(root, wanted) {
  const stack = [root];
  let seen = 0;
  while (stack.length && seen < 4000) {
    const dir = stack.pop();
    let entries = [];
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const entry of entries) {
      if (!entry.isDirectory() || entry.name.startsWith('.') || SKIP_DIRS.has(entry.name)) continue;
      seen += 1;
      const full = path.join(dir, entry.name);
      if (entry.name.toLowerCase() === wanted) return full;
      stack.push(full);
    }
  }
  return '';
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

  const fortniteGame = findNamedDir(root, 'fortnitegame');
  const engine = findNamedDir(root, 'engine');
  if (!fortniteGame && !engine) return { error: 'That folder needs both FortniteGame and Engine.' };
  if (!fortniteGame) return { error: 'That folder is missing FortniteGame.' };
  if (!engine) return { error: 'That folder is missing Engine.' };

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
      if (lower === SHIPPING_EXE) exes.push(full);
      if (lower === 'splash.bmp') splashes.push(full);
    }
  }

  const executablePath =
    exes.find((file) =>
      file.replace(/\\/g, '/').toLowerCase().endsWith('fortnitegame/binaries/win64/fortniteclient-win64-shipping.exe'),
    ) || exes[0];
  if (!executablePath) return { error: 'FortniteClient-Win64-Shipping.exe is missing for this build.' };
  const splashPath = nearestTo(executablePath, splashes);
  const name = buildTitle(executablePath);
  const release = readReleaseIdentity(root, engine, fortniteGame);
  const version = release.gameVersion && release.changelist
    ? `${release.gameVersion}-CL-${release.changelist}`
    : versionFromText(name) || versionFromText(path.basename(root)) || 'Local';
  return {
    folderPath: root,
    executablePath,
    splashPath,
    name,
    version,
    gameVersion: release.gameVersion,
    changelist: release.changelist,
  };
}

function releaseFromText(text) {
  let version = '';
  let changelist = '';
  const inline = String(text).match(/(\d+\.\d+)-CL-(\d+)/i);
  if (inline) {
    version = inline[1];
    changelist = inline[2];
  }
  try {
    const parsed = JSON.parse(text);
    const branch = String(parsed?.BranchName || '');
    const branchVersion = branch.match(/(\d+\.\d+)/);
    const branchCl = branch.match(/CL-(\d+)/i);
    if (branchVersion) version = branchVersion[1];
    if (branchCl) changelist = branchCl[1];
    if (!changelist && parsed?.Changelist != null) {
      const digits = String(parsed.Changelist).replace(/\D/g, '');
      if (digits) changelist = digits;
    }
  } catch {
    /* plain text is enough when it already matched */
  }
  if (!/^\d+\.\d+$/.test(version) || !/^\d{5,12}$/.test(changelist)) return null;
  return { gameVersion: version, changelist };
}

function readReleaseIdentity(root, engine, fortniteGame) {
  const direct = [
    engine ? path.join(engine, 'Build', 'Build.version') : '',
    fortniteGame ? path.join(fortniteGame, 'Build', 'Build.version') : '',
    path.join(root, 'Engine', 'Build', 'Build.version'),
    path.join(root, 'Build.version'),
  ].filter(Boolean);
  for (const file of direct) {
    try {
      const found = releaseFromText(fs.readFileSync(file, 'utf8').slice(0, 8000));
      if (found) return found;
    } catch {
      /* try the next file */
    }
  }
  const stack = [root];
  let seen = 0;
  while (stack.length && seen < 4000) {
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
        const lower = entry.name.toLowerCase();
        if (SKIP_DIRS.has(entry.name) || lower === 'content' || lower === 'paks' || lower === 'binaries') continue;
        stack.push(full);
        continue;
      }
      if (!entry.isFile() || entry.name.toLowerCase() !== 'build.version') continue;
      seen += 1;
      try {
        const found = releaseFromText(fs.readFileSync(full, 'utf8').slice(0, 8000));
        if (found) return found;
      } catch {
        /* unreadable */
      }
    }
  }
  return { gameVersion: '', changelist: '' };
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
