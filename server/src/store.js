import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { avatarDataUrl, itemArt } from './art.js';

const dataDir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'data');
const dbPath = path.join(dataDir, 'db.json');

const DEFAULT_SETTINGS = {
  accent: '#4c8dff',
  mobileBuilds: false,
  resetOnRelease: false,
  potatoGraphics: false,
};

function seedItems() {
  return [
    {
      id: 'nyx-vale',
      name: 'Nyx Vale',
      type: 'skin',
      rarity: 'epic',
      vbucks: 1500,
      image: itemArt.nyxVale,
    },
    {
      id: 'auric-warden',
      name: 'Auric Warden',
      type: 'skin',
      rarity: 'mythic',
      vbucks: 2000,
      image: itemArt.auricWarden,
    },
    {
      id: 'lumen-fox',
      name: 'Lumen Fox',
      type: 'skin',
      rarity: 'rare',
      vbucks: 1200,
      image: itemArt.lumenFox,
    },
    {
      id: 'cinder-pike',
      name: 'Cinder Pike',
      type: 'pickaxe',
      rarity: 'legendary',
      vbucks: 800,
      image: itemArt.cinderPike,
    },
    {
      id: 'grayline',
      name: 'Grayline',
      type: 'pickaxe',
      rarity: 'common',
      vbucks: 500,
      image: itemArt.grayline,
    },
    {
      id: 'orbit-veil',
      name: 'Orbit Veil',
      type: 'glider',
      rarity: 'epic',
      vbucks: 1200,
      image: itemArt.orbitVeil,
    },
    {
      id: 'signal-pop',
      name: 'Signal Pop',
      type: 'emote',
      rarity: 'uncommon',
      vbucks: 300,
      image: itemArt.signalPop,
    },
    {
      id: 'freewheel',
      name: 'Freewheel',
      type: 'emote',
      rarity: 'epic',
      vbucks: 0,
      image: itemArt.freewheel,
    },
  ];
}

function seed() {
  return {
    user: {
      id: '100000000000000001',
      discordId: '100000000000000001',
      discordName: 'Avix',
      displayName: 'Avix',
      role: 'admin',
      avatar: avatarDataUrl(),
      lastNameChangeAt: null,
      equipped: {
        skin: 'nyx-vale',
        emote: null,
        pickaxe: 'grayline',
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
    items: seedItems(),
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
