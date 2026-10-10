import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
function dataDirectory() {
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
      stats: {
        elims: 4821,
        wins: 186,
        matches: 974,
        vbucks: 1350,
      },
    },
    settings: { ...DEFAULT_SETTINGS },
    session: null,
    news: [],
    items: [],
    builds: [],
    selectedBuildId: null,
    rivals: [
      { id: 'vanta', name: 'Vanta', wins: 640, elims: 22110 },
      { id: 'kite', name: 'Kite', wins: 512, elims: 18440 },
      { id: 'mara', name: 'Mara Quin', wins: 151, elims: 9904 },
      { id: 'solen', name: 'Solen', wins: 140, elims: 7420 },
      { id: 'brack', name: 'Brack', wins: 88, elims: 4104 },
    ],
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

export function save() {
  fs.mkdirSync(dataDir, { recursive: true });
  const tmp = `${dbPath}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(getDb(), null, 2));
  fs.renameSync(tmp, dbPath);
}

export { DEFAULT_SETTINGS };
