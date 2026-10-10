import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
export function dataDirectory() {
  if (process.env.NEXA_DATA_DIR) return path.resolve(process.env.NEXA_DATA_DIR);
  try {
    return path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'data');
  } catch {
    return path.join(process.cwd(), 'server', 'data');
  }
}

const dataDir = dataDirectory();
const dbPath = path.join(dataDir, 'db.json');

const DEFAULT_SETTINGS = {
  theme: 'default',
  mobileBuilds: false,
  resetOnRelease: false,
  potatoGraphics: false,
};

const PLACEHOLDER_ID = '100000000000000001';

function defaultAvatar(index) {
  return `https://cdn.discordapp.com/embed/avatars/${index % 6}.png`;
}

function normalizeRivals(rivals) {
  return (Array.isArray(rivals) ? rivals : []).map((rival, index) => {
    const discordName = String(rival?.discordName || rival?.name || 'Player');
    const displayName = String(rival?.displayName || discordName);
    return {
      id: String(rival?.id || `rival-${index}`),
      discordName,
      displayName,
      avatar: rival?.avatar || defaultAvatar(index),
      wins: Number(rival?.wins) || 0,
      elims: Number(rival?.elims) || 0,
    };
  });
}

export function emptyStats() {
  return { elims: 0, wins: 0, matches: 0, vbucks: 0 };
}

export function applyZeroStats(parsed) {
  let dirty = false;
  const stats = parsed.user?.stats || {};
  if (stats.elims !== 0 || stats.wins !== 0 || stats.matches !== 0 || stats.vbucks !== 0) dirty = true;
  parsed.user.stats = emptyStats();
  const source = Array.isArray(parsed.rivals) ? parsed.rivals : [];
  if (source.some((rival) => Number(rival?.wins) || Number(rival?.elims))) dirty = true;
  parsed.rivals = normalizeRivals(source).map((rival) => ({ ...rival, wins: 0, elims: 0 }));
  return dirty;
}

function seed() {
  return {
    user: {
      id: '',
      discordId: '',
      discordName: '',
      displayName: '',
      role: 'player',
      avatar: '',
      lastNameChangeAt: null,
      equipped: {
        skin: null,
        emote: null,
        pickaxe: null,
        glider: null,
      },
      stats: emptyStats(),
    },
    settings: { ...DEFAULT_SETTINGS },
    session: null,
    news: [],
    items: [],
    builds: [],
    selectedBuildId: null,
    rivals: normalizeRivals([
      { id: 'vanta', discordName: 'Vanta', wins: 0, elims: 0 },
      { id: 'kite', discordName: 'Kite', wins: 0, elims: 0 },
      { id: 'mara', discordName: 'Mara Quin', wins: 0, elims: 0 },
      { id: 'solen', discordName: 'Solen', wins: 0, elims: 0 },
      { id: 'brack', discordName: 'Brack', wins: 0, elims: 0 },
    ]),
  };
}

let db = null;

function load() {
  fs.mkdirSync(dataDir, { recursive: true });
  if (!fs.existsSync(dbPath)) {
    const fresh = seed();
    fs.writeFileSync(dbPath, JSON.stringify(fresh, null, 2));
    return fresh;
  }
  try {
    const parsed = JSON.parse(fs.readFileSync(dbPath, 'utf8'));
    if (!parsed?.user || !Array.isArray(parsed.items)) throw new Error('bad db');
    parsed.settings = { ...DEFAULT_SETTINGS, ...(parsed.settings || {}) };
    if (!parsed.settings.theme) parsed.settings.theme = 'default';
    if (parsed.user.discordId === PLACEHOLDER_ID) {
      parsed.session = null;
      parsed.user.id = '';
      parsed.user.discordId = '';
      parsed.user.discordName = '';
      parsed.user.displayName = '';
      parsed.user.avatar = '';
      parsed.user.role = 'player';
    }
    parsed.items = (Array.isArray(parsed.items) ? parsed.items : []).filter(
      (item) => item && item.id !== 'chani' && item.image !== '/shop-chani.png',
    );
    const zeroed = applyZeroStats(parsed);
    if (zeroed) persist(parsed);
    return parsed;
  } catch (error) {
    console.error('[nexa-api] Could not read data file, reseeding.', error.message);
    const fresh = seed();
    fs.writeFileSync(dbPath, JSON.stringify(fresh, null, 2));
    return fresh;
  }
}

export function getDb() {
  if (!db) db = load();
  return db;
}

function persist(data) {
  fs.mkdirSync(dataDir, { recursive: true });
  const tmp = `${dbPath}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2));
  fs.renameSync(tmp, dbPath);
}

export function save() {
  persist(getDb());
}

export { DEFAULT_SETTINGS };
