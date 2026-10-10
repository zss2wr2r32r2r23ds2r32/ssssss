import express from 'express';
import cors from 'cors';
import crypto from 'node:crypto';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { getDb, save, DEFAULT_SETTINGS } from './store.js';
import { cleanDisplayName, cooldownState, formatRemaining } from './profanity.js';
import { shopDayKey, shopRefresh } from './time.js';
import { findShippingBuild } from './builds.js';
import { beginDiscordLogin, oauthCallbackReady, pollDiscordLogin, startDiscordCallback, submitBotIdentity, DISCORD_REDIRECT_URI } from './discord-auth.js';
import { discordUserIsAdmin } from './roles.js';
import { pullShopItems } from './shop-feed.js';
import { APP_VERSION, applyUpdate, updateStatus } from './update.js';

const THEMES = new Set(['default', 'void', 'ember', 'frost', 'jade', 'bud', 'rose', 'sunset', 'aurora', 'tom', 'noir', 'dark']);

const PORT = Number(process.env.PORT || 4177);
const app = express();

app.use(cors({ origin: [/^http:\/\/localhost:\d+$/, /^http:\/\/127\.0\.0\.1:\d+$/] }));
app.use(express.json({ limit: '8mb' }));
app.use((req, _res, next) => {
  if (req.url === '/api') req.url = '/';
  else if (req.url.startsWith('/api/')) req.url = req.url.slice(4);
  next();
});

const TYPES = new Set(['skin', 'emote', 'pickaxe', 'glider', 'backbling', 'wrap']);
const RARITIES = new Set(['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythic']);
const SECTIONS = new Set(['featured', 'daily']);
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

async function syncRole(user) {
  if (!user?.discordId) return;
  const admin = await discordUserIsAdmin(user.discordId);
  const next = admin ? 'admin' : 'player';
  if (user.role !== next) {
    user.role = next;
    save();
  }
}

function requireAuth(req, res, next) {
  const token = readToken(req);
  const db = getDb();
  if (!token || !db.session || db.session.token !== token) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  syncRole(db.user)
    .then(() => {
      req.user = getDb().user;
      next();
    })
    .catch(next);
}

function requireAdmin(req, res, next) {
  if (req.user?.role !== 'admin') return res.status(403).json({ error: 'Admin only.' });
  next();
}

function validImage(image) {
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

app.post('/auth/discord/bot', (req, res) => {
  const result = submitBotIdentity({
    id: req.body?.id,
    username: req.body?.username,
    avatarUrl: req.body?.avatar,
  });
  if (result.error) return res.status(409).json({ error: result.error });
  res.json({ ok: true });
});

app.get('/launcher/version', (_req, res) => {
  res.json({ version: APP_VERSION });
});

app.get('/launcher/update', async (_req, res) => {
  try {
    res.json(await updateStatus());
  } catch (error) {
    res.status(502).json({ error: error instanceof Error ? error.message : 'Could not check for updates.' });
  }
});

app.post('/launcher/update/apply', async (_req, res) => {
  try {
    res.json(await applyUpdate());
  } catch (error) {
    res.status(502).json({ error: error instanceof Error ? error.message : 'Could not install the update.' });
  }
});

app.post('/auth/discord/start', (_req, res) => {
  if (!oauthCallbackReady()) {
    return res.status(503).json({
      error: `Discord sign-in needs ${DISCORD_REDIRECT_URI} to be open. Restart Nexa and try again.`,
    });
  }
  res.json(beginDiscordLogin());
});

app.get('/auth/discord/pending', (req, res) => {
  res.json(pollDiscordLogin(req.query.state));
});

app.post('/auth/logout', requireAuth, (_req, res) => {
  const db = getDb();
  db.session = null;
  save();
  res.json({ ok: true });
});

app.get('/me', async (req, res) => {
  const token = readToken(req);
  const db = getDb();
  if (token && (!db.session || db.session.token !== token)) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  if (token && db.session?.token === token) await syncRole(db.user);
  res.json(presentUser(db));
});

function visibleName(discordName, displayName) {
  const discord = String(discordName || '').trim();
  const display = String(displayName || '').trim();
  if (display && discord && display.toLowerCase() !== discord.toLowerCase()) return display;
  return discord || display;
}

function nameTaken(db, name) {
  const needle = name.toLowerCase();
  return db.rivals.some((rival) => {
    const shown = visibleName(rival.discordName, rival.displayName).toLowerCase();
    return (
      shown === needle ||
      String(rival.displayName || '').toLowerCase() === needle ||
      String(rival.discordName || '').toLowerCase() === needle
    );
  });
}

app.patch('/me', requireAuth, (req, res) => {
  const db = getDb();
  const cleaned = cleanDisplayName(req.body?.displayName);
  if (cleaned.error) return res.status(400).json({ error: cleaned.error });
  if (cleaned.name.toLowerCase() === db.user.displayName.toLowerCase()) {
    return res.status(400).json({ error: "That's already your display name." });
  }
  if (nameTaken(db, cleaned.name)) {
    return res.status(400).json({ error: 'That display name is already used by another player.' });
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
  const requested = typeof body.theme === 'string' ? body.theme.trim().toLowerCase() : '';
  const db = getDb();
  const theme = THEMES.has(requested) ? requested : THEMES.has(db.settings.theme) ? db.settings.theme : 'default';
  db.settings = {
    theme,
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
  const image = typeof req.body?.image === 'string' ? req.body.image.trim() : '';
  if (image && !validImage(image)) {
    return res.status(400).json({ error: 'That image needs to be a URL or an upload.' });
  }
  const db = getDb();
  const item = {
    id: crypto.randomUUID(),
    title,
    body,
    image: image || null,
    createdAt: new Date().toISOString(),
    author: db.user.displayName,
  };
  db.news.unshift(item);
  db.news = db.news.slice(0, 40);
  save();
  res.status(201).json(item);
});

app.delete('/news/:id', requireAuth, requireAdmin, (req, res) => {
  const db = getDb();
  const before = db.news.length;
  db.news = db.news.filter((item) => item.id !== req.params.id);
  if (db.news.length === before) return res.status(404).json({ error: 'Post not found.' });
  save();
  res.json({ news: db.news });
});

app.get('/shop/items', (_req, res) => {
  res.json({ items: getDb().items });
});

app.post('/shop/items', requireAuth, requireAdmin, (req, res) => {
  const name = typeof req.body?.name === 'string' ? req.body.name.trim() : '';
  const type = req.body?.type;
  const rarity = req.body?.rarity;
  const vbucks = Number(req.body?.vbucks);
  const image = typeof req.body?.image === 'string' ? req.body.image.trim() : '';
  const section = req.body?.section;
  if (name.length < 2 || name.length > 32) {
    return res.status(400).json({ error: 'Item name must be 2–32 characters.' });
  }
  if (!TYPES.has(type)) return res.status(400).json({ error: 'Pick a type.' });
  if (!RARITIES.has(rarity)) return res.status(400).json({ error: 'Pick a rarity.' });
  if (!Number.isInteger(vbucks) || vbucks < 0 || vbucks > 100000) {
    return res.status(400).json({ error: 'V-Bucks must be a whole number from 0 to 100000.' });
  }
  if (!SECTIONS.has(section)) return res.status(400).json({ error: 'Pick Featured or Daily.' });
  if (!validImage(image)) {
    return res.status(400).json({ error: 'Import an image for this item.' });
  }
  const db = getDb();
  const item = {
    id: crypto.randomUUID(),
    name,
    type,
    rarity,
    vbucks,
    image,
    section,
  };
  db.items.push(item);
  save();
  res.status(201).json(item);
});

app.delete('/shop/items/:id', requireAuth, requireAdmin, (req, res) => {
  const db = getDb();
  const before = db.items.length;
  db.items = db.items.filter((item) => item.id !== req.params.id);
  if (db.items.length === before) return res.status(404).json({ error: 'Item not found.' });
  for (const slot of ['skin', 'emote', 'pickaxe', 'glider']) {
    if (db.user.equipped?.[slot] === req.params.id) db.user.equipped[slot] = null;
  }
  save();
  res.json({ items: db.items });
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
  const found = findShippingBuild(req.body?.folderPath);
  if (found.error) return res.status(400).json({ error: found.error });
  res.json({ exists: true, version: found.version, executablePath: found.executablePath });
});

app.post('/builds/import', requireAuth, (req, res) => {
  const found = findShippingBuild(req.body?.folderPath);
  if (found.error) return res.status(400).json({ error: found.error });
  const db = getDb();
  if (db.builds.some((build) => build.executablePath === found.executablePath)) {
    return res.status(409).json({ error: 'That build is already in your library.' });
  }
  const build = {
    id: crypto.randomUUID(),
    name: found.name,
    version: found.version,
    folderPath: found.folderPath,
    executablePath: found.executablePath,
    splashPath: found.splashPath,
    gameVersion: found.gameVersion || '',
    changelist: found.changelist || '',
    source: 'local',
    createdAt: new Date().toISOString(),
  };
  db.builds.push(build);
  db.selectedBuildId = build.id;
  save();
  res.status(201).json({ build, builds: db.builds, selectedId: db.selectedBuildId });
});

app.get('/builds/:id/splash', (req, res) => {
  const build = getDb().builds.find((entry) => entry.id === req.params.id);
  if (!build?.splashPath || !fs.existsSync(build.splashPath)) return res.status(404).end();
  res.type('image/bmp');
  res.sendFile(path.resolve(build.splashPath));
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

app.post('/builds/launch', requireAuth, (req, res) => {
  const db = getDb();
  const requested = typeof req.body?.id === 'string' ? req.body.id : db.selectedBuildId;
  const build = db.builds.find((entry) => entry.id === requested);
  if (!build) return res.status(400).json({ error: 'No build is selected.' });
  let executable = '';
  try {
    executable = fs.realpathSync(build.executablePath || '');
  } catch {
    executable = '';
  }
  const shippingName = path.basename(executable).toLowerCase();
  if (shippingName !== 'fortniteclient-win64-shipping.exe' || shippingName.includes('epicgameslauncher')) {
    return res.status(400).json({ error: 'FortniteClient-Win64-Shipping.exe is missing for this build.' });
  }
  db.selectedBuildId = build.id;
  save();
  const child = spawn(executable, [], {
    detached: true,
    stdio: 'ignore',
    windowsHide: true,
    shell: false,
    cwd: path.dirname(executable),
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
    ...db.rivals.map((rival) => ({
      id: rival.id,
      name: visibleName(rival.discordName, rival.displayName),
      discordName: rival.discordName,
      displayName: rival.displayName,
      avatar: rival.avatar,
      wins: rival.wins,
      elims: rival.elims,
      points: rival.wins * 100 + rival.elims,
      you: false,
    })),
    {
      id: db.user.id,
      name: visibleName(db.user.discordName, db.user.displayName),
      discordName: db.user.discordName,
      displayName: db.user.displayName,
      avatar: db.user.avatar,
      wins: db.user.stats.wins,
      elims: db.user.stats.elims,
      points: db.user.stats.wins * 100 + db.user.stats.elims,
      you: true,
    },
  ];
  const by = req.query.by === 'elims' || req.query.by === 'points' ? req.query.by : 'wins';
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

startDiscordCallback();

let shopRefreshBusy = false;

async function refreshShopIfDue() {
  const key = shopDayKey();
  const current = getDb();
  if (current.shopDay === key && current.shopSource === 'c2s2' && Array.isArray(current.items) && current.items.length > 0) return;
  if (shopRefreshBusy) return;
  shopRefreshBusy = true;
  try {
    const items = await pullShopItems();
    if (!items.length) return;
    const next = getDb();
    if (next.shopDay === key && next.shopSource === 'c2s2' && Array.isArray(next.items) && next.items.length > 0) return;
    next.items = items;
    next.shopDay = key;
    next.shopSource = 'c2s2';
    save();
    console.log(`[nexa-api] shop refreshed for ${key} (${items.length} items)`);
  } catch (error) {
    console.error('[nexa-api] shop refresh failed', error instanceof Error ? error.message : error);
  } finally {
    shopRefreshBusy = false;
  }
}

const httpServer = app.listen(PORT, '127.0.0.1', () => {
  console.log(`[nexa-api] listening on http://127.0.0.1:${PORT}`);
  refreshShopIfDue();
  setInterval(refreshShopIfDue, 30_000);
});
httpServer.on('error', (error) => {
  console.error('[nexa-api] listen failed:', error.message);
});

export { httpServer };
