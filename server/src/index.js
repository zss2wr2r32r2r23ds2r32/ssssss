import express from 'express';
import cors from 'cors';
import crypto from 'node:crypto';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { getDb, save, DEFAULT_SETTINGS } from './store.js';
import { cleanDisplayName, cooldownState, formatRemaining } from './profanity.js';
import { shopRefresh } from './time.js';
import { inspectFolder } from './builds.js';

const PORT = Number(process.env.PORT || 4177);
const app = express();

app.use(cors({ origin: [/^http:\/\/localhost:\d+$/, /^http:\/\/127\.0\.0\.1:\d+$/] }));
app.use(express.json({ limit: '8mb' }));
app.use((req, _res, next) => {
  if (req.url === '/api') req.url = '/';
  else if (req.url.startsWith('/api/')) req.url = req.url.slice(4);
  next();
});

const TYPES = new Set(['skin', 'emote', 'pickaxe', 'glider']);
const RARITIES = new Set(['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythic']);
const CATALOG = [
  { name: 'Chapter 2 Season 2', version: '12.41' },
  { name: 'Chapter 2 Season 1', version: '11.31' },
  { name: 'Chapter 1 Season 9', version: '9.40' },
  { name: 'Chapter 1 Season 8', version: '8.51' },
];

function presentUser(db) {
  const user = db.user;
  const equip = (id) => db.items.find((item) => item.id === id) || null;
  const cooldown = cooldownState(user.lastNameChangeAt);
  return {
    id: user.id,
    discordId: user.discordId,
    discordName: user.discordName,
    displayName: user.displayName,
    role: user.role,
    avatar: user.avatar,
    equipped: {
      skin: equip(user.equipped.skin),
      emote: equip(user.equipped.emote),
      pickaxe: equip(user.equipped.pickaxe),
      glider: equip(user.equipped.glider),
    },
    nameChange: {
      allowed: cooldown.allowed,
      remainingMs: cooldown.remainingMs,
    },
  };
}

function readToken(req) {
  const header = req.headers.authorization || '';
  return header.startsWith('Bearer ') ? header.slice(7) : '';
}

function requireAuth(req, res, next) {
  const token = readToken(req);
  const db = getDb();
  if (!token || !db.session || db.session.token !== token) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  req.user = db.user;
  next();
}

function requireAdmin(req, res, next) {
  if (req.user?.role !== 'admin') return res.status(403).json({ error: 'Admin only.' });
  next();
}

const BUNDLED_SKINS = {
  chani: '/shop-chani.png',
  default: '/skin-default.png',
  portrait: '/skin-default.png',
  'default portrait': '/skin-default.png',
  'default skin': '/skin-default.png',
};
const LOCAL_IMAGES = new Set(['/shop-chani.png', '/skin-default.png']);

function bundledImageForName(name) {
  return BUNDLED_SKINS[String(name || '').trim().toLowerCase()] || '';
}

function validImage(image) {
  if (LOCAL_IMAGES.has(image)) return true;
  if (typeof image !== 'string' || image.length < 12 || image.length > 1_500_000) return false;
  if (image.startsWith('data:image/')) return true;
  try {
    const url = new URL(image);
    return url.protocol === 'https:' || url.protocol === 'http:';
  } catch {
    return false;
  }
}

app.get('/health', (_req, res) => {
  res.json({ ok: true, name: 'nexa' });
});

app.post('/auth/dev-login', (_req, res) => {
  const db = getDb();
  const token = crypto.randomBytes(24).toString('hex');
  db.session = { token, userId: db.user.id };
  save();
  res.json({ token, user: presentUser(db) });
});

app.post('/auth/logout', requireAuth, (_req, res) => {
  const db = getDb();
  db.session = null;
  save();
  res.json({ ok: true });
});

app.get('/me', (req, res) => {
  const token = readToken(req);
  const db = getDb();
  if (token && (!db.session || db.session.token !== token)) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  res.json(presentUser(db));
});

app.patch('/me', requireAuth, (req, res) => {
  const db = getDb();
  const cleaned = cleanDisplayName(req.body?.displayName);
  if (cleaned.error) return res.status(400).json({ error: cleaned.error });
  if (cleaned.name.toLowerCase() === db.user.displayName.toLowerCase()) {
    return res.status(400).json({ error: "That's already your display name." });
  }
  const cooldown = cooldownState(db.user.lastNameChangeAt);
  if (!cooldown.allowed) {
    return res.status(400).json({
      error: `You can change your name again in ${formatRemaining(cooldown.remainingMs)}.`,
    });
  }
  db.user.displayName = cleaned.name;
  db.user.lastNameChangeAt = new Date().toISOString();
  save();
  res.json(presentUser(db));
});

app.get('/settings', (_req, res) => {
  res.json({ ...DEFAULT_SETTINGS, ...getDb().settings });
});

app.put('/settings', requireAuth, (req, res) => {
  const body = req.body || {};
  const accent = typeof body.accent === 'string' ? body.accent.trim().toLowerCase() : getDb().settings.accent;
  if (!/^#[0-9a-fA-F]{6}$/.test(accent)) {
    return res.status(400).json({ error: 'Accent color must be a hex value like #4C8DFF.' });
  }
  const db = getDb();
  db.settings = {
    accent,
    mobileBuilds: Boolean(body.mobileBuilds),
    resetOnRelease: Boolean(body.resetOnRelease),
    potatoGraphics: Boolean(body.potatoGraphics),
  };
  save();
  res.json(db.settings);
});

app.get('/stats', (_req, res) => {
  res.json(getDb().user.stats);
});

app.get('/news', (_req, res) => {
  res.json({ news: getDb().news });
});

app.post('/news', requireAuth, requireAdmin, (req, res) => {
  const title = typeof req.body?.title === 'string' ? req.body.title.trim() : '';
  const body = typeof req.body?.body === 'string' ? req.body.body.trim() : '';
  if (title.length < 1 || title.length > 80) {
    return res.status(400).json({ error: 'Title must be 1–80 characters.' });
  }
  if (body.length < 1 || body.length > 600) {
    return res.status(400).json({ error: 'Body must be 1–600 characters.' });
  }
  const db = getDb();
  const item = {
    id: crypto.randomUUID(),
    title,
    body,
    createdAt: new Date().toISOString(),
    author: db.user.displayName,
  };
  db.news.unshift(item);
  db.news = db.news.slice(0, 40);
  save();
  res.status(201).json(item);
});

app.get('/shop/items', (_req, res) => {
  res.json({ items: getDb().items });
});

app.post('/shop/items', requireAuth, requireAdmin, (req, res) => {
  const name = typeof req.body?.name === 'string' ? req.body.name.trim() : '';
  const type = req.body?.type;
  const rarity = req.body?.rarity;
  const vbucks = Number(req.body?.vbucks);
  let image = typeof req.body?.image === 'string' ? req.body.image.trim() : '';
  if (!image) image = bundledImageForName(name);
  if (name.length < 2 || name.length > 32) {
    return res.status(400).json({ error: 'Item name must be 2–32 characters.' });
  }
  if (!TYPES.has(type)) return res.status(400).json({ error: 'Pick a type.' });
  if (!RARITIES.has(rarity)) return res.status(400).json({ error: 'Pick a rarity.' });
  if (!Number.isInteger(vbucks) || vbucks < 0 || vbucks > 100000) {
    return res.status(400).json({ error: 'V-Bucks must be a whole number from 0 to 100000.' });
  }
  if (!validImage(image)) {
    return res.status(400).json({ error: 'Add an image URL or upload a picture.' });
  }
  const db = getDb();
  const item = {
    id: crypto.randomUUID(),
    name,
    type,
    rarity,
    vbucks,
    image,
  };
  db.items.push(item);
  save();
  res.status(201).json(item);
});

app.post('/shop/equip', requireAuth, (req, res) => {
  const db = getDb();
  const item = db.items.find((entry) => entry.id === req.body?.itemId);
  if (!item) return res.status(404).json({ error: 'Item not found.' });
  db.user.equipped[item.type] = item.id;
  save();
  res.json({ equipped: presentUser(db).equipped, item });
});

app.get('/shop/refresh', (_req, res) => {
  res.json(shopRefresh());
});

app.get('/builds/catalog', (_req, res) => {
  res.json({ seasons: CATALOG });
});

app.get('/builds', (_req, res) => {
  const db = getDb();
  res.json({ builds: db.builds, selectedId: db.selectedBuildId });
});

app.post('/builds/detect', requireAuth, (req, res) => {
  res.json(inspectFolder(req.body?.folderPath));
});

app.post('/builds', requireAuth, (req, res) => {
  const source = req.body?.source === 'catalog' ? 'catalog' : 'local';
  const db = getDb();

  if (source === 'catalog') {
    const name = typeof req.body?.name === 'string' ? req.body.name.trim() : '';
    const known = CATALOG.find((entry) => entry.name === name);
    if (!known) return res.status(400).json({ error: 'That season is not in the catalog.' });
    if (db.builds.some((build) => build.source === 'catalog' && build.name === known.name)) {
      return res.status(409).json({ error: 'That build is already in your library.' });
    }
    const build = {
      id: crypto.randomUUID(),
      name: known.name,
      version: known.version,
      folderPath: null,
      executablePath: null,
      source: 'catalog',
      createdAt: new Date().toISOString(),
    };
    db.builds.push(build);
    if (!db.selectedBuildId) db.selectedBuildId = build.id;
    save();
    return res.status(201).json({ build, builds: db.builds, selectedId: db.selectedBuildId });
  }

  const folderPath = typeof req.body?.folderPath === 'string' ? req.body.folderPath.trim() : '';
  if (!folderPath) return res.status(400).json({ error: 'Enter a folder path.' });
  const inspected = inspectFolder(folderPath);
  const version = (typeof req.body?.version === 'string' ? req.body.version.trim() : '') || inspected.version || '';
  if (!version) return res.status(400).json({ error: 'Could not detect a version. Enter one.' });
  if (version.length > 48) return res.status(400).json({ error: 'Version label is too long.' });
  const name = (typeof req.body?.name === 'string' ? req.body.name.trim() : '') || version;
  if (name.length < 1 || name.length > 48) {
    return res.status(400).json({ error: 'Name must be 1–48 characters.' });
  }
  const build = {
    id: crypto.randomUUID(),
    name,
    version,
    folderPath,
    executablePath: inspected.executablePath,
    source: 'local',
    createdAt: new Date().toISOString(),
  };
  db.builds.push(build);
  if (!db.selectedBuildId) db.selectedBuildId = build.id;
  save();
  res.status(201).json({ build, builds: db.builds, selectedId: db.selectedBuildId });
});

app.post('/builds/select', requireAuth, (req, res) => {
  const db = getDb();
  const build = db.builds.find((entry) => entry.id === req.body?.id);
  if (!build) return res.status(404).json({ error: 'Build not found.' });
  db.selectedBuildId = build.id;
  save();
  res.json({ selectedId: db.selectedBuildId });
});

app.delete('/builds/:id', requireAuth, (req, res) => {
  const db = getDb();
  const before = db.builds.length;
  db.builds = db.builds.filter((build) => build.id !== req.params.id);
  if (db.builds.length === before) return res.status(404).json({ error: 'Build not found.' });
  if (db.selectedBuildId === req.params.id) db.selectedBuildId = db.builds[0]?.id || null;
  save();
  res.json({ builds: db.builds, selectedId: db.selectedBuildId });
});

app.post('/builds/launch', requireAuth, (_req, res) => {
  const db = getDb();
  const build = db.builds.find((entry) => entry.id === db.selectedBuildId);
  if (!build?.executablePath || !fs.existsSync(build.executablePath)) {
    return res.status(400).json({ error: 'No build is selected.' });
  }
  try {
    fs.accessSync(build.executablePath, fs.constants.X_OK);
  } catch {
    return res.status(400).json({ error: 'No build is selected.' });
  }
  const child = spawn(build.executablePath, [], {
    detached: true,
    stdio: 'ignore',
    windowsHide: true,
  });
  child.on('error', (error) => {
    console.error('[nexa-api] launch failed', error.message);
  });
  child.unref();
  res.json({ ok: true, name: build.name });
});

app.get('/leaderboard', (req, res) => {
  const db = getDb();
  const rows = [
    ...db.rivals.map((rival) => ({ ...rival, you: false })),
    {
      id: db.user.id,
      name: db.user.displayName,
      wins: db.user.stats.wins,
      elims: db.user.stats.elims,
      you: true,
    },
  ];
  const by = req.query.by === 'elims' ? 'elims' : 'wins';
  rows.sort((a, b) => b[by] - a[by] || b.wins - a.wins || a.name.localeCompare(b.name));
  res.json({ rows, by });
});

const API_PREFIXES = ['/auth', '/me', '/settings', '/stats', '/news', '/shop', '/builds', '/leaderboard', '/health'];

if (process.env.NEXA_STATIC) {
  const root = path.resolve(process.env.NEXA_STATIC);
  app.use(express.static(root, { index: false }));
  app.use((req, res) => {
    const isApi = API_PREFIXES.some((prefix) => req.path === prefix || req.path.startsWith(`${prefix}/`));
    if (req.method === 'GET' && !isApi) {
      res.sendFile(path.join(root, 'index.html'));
      return;
    }
    res.status(404).json({ error: `No route ${req.method} ${req.path}` });
  });
} else {
  app.use((req, res) => {
    res.status(404).json({ error: `No route ${req.method} ${req.path}` });
  });
}

const httpServer = app.listen(PORT, '127.0.0.1', () => {
  console.log(`[nexa-api] listening on http://127.0.0.1:${PORT}`);
});
httpServer.on('error', (error) => {
  console.error('[nexa-api] listen failed:', error.message);
});

export { httpServer };
