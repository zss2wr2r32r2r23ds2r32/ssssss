import crypto from 'node:crypto';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { loadEnv } from './env.js';
import { discordUserIsAdmin } from './roles.js';
import { getDb, save } from './store.js';

loadEnv();

export const DISCORD_CLIENT_ID = '1558290151124369440';
export const DISCORD_REDIRECT_URI = 'http://127.0.0.1:4390/callback';
const AUTHORIZE_BASE =
  'https://discord.com/oauth2/authorize?client_id=1558290151124369440&response_type=code&redirect_uri=http%3A%2F%2F127.0.0.1%3A4390%2Fcallback&scope=identify';

const pending = new Map();
let callbackListening = false;

function base64url(buffer) {
  return Buffer.from(buffer).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
}

export function oauthCallbackReady() {
  return callbackListening;
}

export function beginDiscordLogin() {
  const state = crypto.randomBytes(24).toString('hex');
  const verifier = base64url(crypto.randomBytes(32));
  const challenge = base64url(crypto.createHash('sha256').update(verifier).digest());
  pending.set(state, { verifier, status: 'waiting', createdAt: Date.now(), error: '', token: '' });
  const url = `${AUTHORIZE_BASE}&state=${encodeURIComponent(state)}&code_challenge=${encodeURIComponent(challenge)}&code_challenge_method=S256`;
  return { url, state };
}

export function pollDiscordLogin(state) {
  const row = typeof state === 'string' ? pending.get(state) : null;
  if (!row) return { status: 'missing' };
  if (Date.now() - row.createdAt > 10 * 60 * 1000) {
    pending.delete(state);
    return { status: 'error', error: 'Discord sign-in expired. Try again.' };
  }
  if (row.status === 'error') return { status: 'error', error: row.error || 'Discord sign-in failed.' };
  if (row.status === 'ready') return { status: 'ready', token: row.token };
  return { status: row.status };
}

export function submitBotIdentity(profile) {
  const id = String(profile?.id || '');
  const username = String(profile?.username || '').trim();
  const picture = String(profile?.avatarUrl || profile?.avatar || '').trim();
  if (!/^\d{15,22}$/.test(id) || !username) return { error: 'Discord user is missing.' };
  const rows = [...pending.entries()].filter(([, row]) => row.status === 'waiting' || row.status === 'needs-bot');
  if (!rows.length) return { error: 'Click Continue with Discord in Nexa first.' };
  rows.sort((a, b) => b[1].createdAt - a[1].createdAt);
  const [, row] = rows[0];
  row.profile = { id, username, avatarUrl: picture || avatarUrl({ id, avatar: null }) };
  row.status = 'authorized';
  return { ok: true };
}

function avatarUrl(user) {
  if (user.avatar) {
    const ext = user.avatar.startsWith('a_') ? 'gif' : 'png';
    return `https://cdn.discordapp.com/avatars/${user.id}/${user.avatar}.${ext}?size=128`;
  }
  try {
    const index = Number(BigInt(user.id) >> 22n) % 6;
    return `https://cdn.discordapp.com/embed/avatars/${index}.png`;
  } catch {
    return 'https://cdn.discordapp.com/embed/avatars/0.png';
  }
}

async function applyProfile(profile) {
  const db = getDb();
  const same = db.user.discordId === profile.id;
  db.user.id = profile.id;
  db.user.discordId = profile.id;
  db.user.discordName = profile.username;
  db.user.role = (await discordUserIsAdmin(profile.id)) ? 'admin' : 'player';
  db.user.avatar = profile.avatarUrl || avatarUrl({ id: profile.id, avatar: null });
  if (!same || !db.user.displayName) {
    db.user.displayName = String(profile.username || 'Player').slice(0, 16);
    db.user.lastNameChangeAt = null;
  }
  const token = crypto.randomBytes(24).toString('hex');
  db.session = { token, userId: db.user.id };
  save();
  return token;
}

async function tokenRequest(params) {
  const response = await fetch('https://discord.com/api/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(params),
  });
  const data = await response.json().catch(() => ({}));
  return { ok: response.ok, status: response.status, data };
}

function secretRequired(result) {
  const blob = `${result.status} ${JSON.stringify(result.data || {})}`;
  return /client_secret|invalid_client|unauthorized/i.test(blob);
}

async function exchangeCode(code, verifier) {
  const params = {
    client_id: DISCORD_CLIENT_ID,
    grant_type: 'authorization_code',
    code,
    redirect_uri: DISCORD_REDIRECT_URI,
    code_verifier: verifier,
  };
  let result = await tokenRequest(params);
  const secret = process.env.DISCORD_CLIENT_SECRET?.trim();
  if (!result.ok && secret) {
    result = await tokenRequest({ ...params, client_secret: secret });
  }
  if (!result.ok) {
    if (!secret && secretRequired(result)) {
      throw new Error(
        'Discord refused the code exchange without a client secret. Set DISCORD_CLIENT_SECRET in the environment (or server/.env) and try again. Do not commit that secret.',
      );
    }
    const detail = result.data?.error_description || result.data?.error || 'Discord token exchange failed.';
    throw new Error(detail);
  }
  return result.data.access_token;
}

async function fetchIdentity(accessToken) {
  const response = await fetch('https://discord.com/api/users/@me', {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok || !data?.id || !data?.username) {
    throw new Error('Discord did not return an identity for this login.');
  }
  return { id: String(data.id), username: String(data.username), avatarUrl: avatarUrl(data) };
}

function readBotToken() {
  const fromEnv = process.env.DISCORD_TOKEN?.trim();
  if (fromEnv) return fromEnv;
  const candidates = [path.join(process.cwd(), 'bot', '.env'), path.join(process.cwd(), '..', 'bot', '.env')];
  for (const file of candidates) {
    try {
      const line = fs
        .readFileSync(file, 'utf8')
        .split(/\r?\n/)
        .find((entry) => entry.startsWith('DISCORD_TOKEN='));
      const value = line?.slice('DISCORD_TOKEN='.length).trim().replace(/^['"]|['"]$/g, '');
      if (value) return value;
    } catch {
      /* no bot env in this location */
    }
  }
  return '';
}

function snowflakeTime(id) {
  try {
    return Number((BigInt(id) >> 22n) + 1420070400000n);
  } catch {
    return 0;
  }
}

async function identityFromGuild(guildId) {
  const token = readBotToken();
  if (!token || !guildId) return null;
  const response = await fetch(
    `https://discord.com/api/v10/guilds/${guildId}/audit-logs?action_type=28&limit=5`,
    { headers: { Authorization: `Bot ${token}` } },
  );
  if (!response.ok) return null;
  const data = await response.json().catch(() => ({}));
  const now = Date.now();
  const entry = (data.audit_log_entries || []).find((row) => now - snowflakeTime(row.id) < 3 * 60 * 1000);
  if (!entry?.user_id) return null;
  const listed = (data.users || []).find((row) => row.id === entry.user_id);
  if (listed?.id && listed?.username) {
    return { id: String(listed.id), username: String(listed.username), avatarUrl: avatarUrl(listed) };
  }
  const lookup = await fetch(`https://discord.com/api/v10/users/${entry.user_id}`, {
    headers: { Authorization: `Bot ${token}` },
  });
  const body = await lookup.json().catch(() => ({}));
  if (!lookup.ok || !body?.id || !body?.username) return null;
  return { id: String(body.id), username: String(body.username), avatarUrl: avatarUrl(body) };
}

function logoDataUrl() {
  const candidates = [
    process.env.NEXA_STATIC ? path.join(process.env.NEXA_STATIC, 'logo.png') : '',
    path.join(process.cwd(), 'launcher', 'public', 'logo.png'),
    path.join(process.cwd(), '..', 'launcher', 'public', 'logo.png'),
  ].filter(Boolean);
  for (const file of candidates) {
    try {
      return `data:image/png;base64,${fs.readFileSync(file).toString('base64')}`;
    } catch {
      /* try the next location */
    }
  }
  return '';
}

function fontDataUrl() {
  const candidates = [
    process.env.NEXA_STATIC ? path.join(process.env.NEXA_STATIC, 'fonts', 'plus-jakarta-sans.woff2') : '',
    path.join(process.cwd(), 'launcher', 'public', 'fonts', 'plus-jakarta-sans.woff2'),
    path.join(process.cwd(), '..', 'launcher', 'public', 'fonts', 'plus-jakarta-sans.woff2'),
  ].filter(Boolean);
  for (const file of candidates) {
    try {
      return `data:font/woff2;base64,${fs.readFileSync(file).toString('base64')}`;
    } catch {
      /* try the next location */
    }
  }
  return '';
}

export function renderReadyPage({ username, avatarUrl: picture, state }) {
  const logo = logoDataUrl();
  const font = fontDataUrl();
  const safeName = escapeHtml(username);
  const safeAvatar = escapeHtml(picture);
  const safeState = JSON.stringify(state);
  const logoTag = logo ? `<img class="logo" src="${logo}" alt="Nexa" />` : '';
  const fontFace = font
    ? `@font-face{font-family:"Plus Jakarta Sans";src:url("${font}") format("woff2");font-weight:100 800;font-display:swap;}`
    : '';
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Nexa</title>
  <style>
    ${fontFace}
    :root { color-scheme: dark; }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      display: grid;
      place-items: center;
      background: #07080c;
      color: #f5f7fb;
      font-family: "Plus Jakarta Sans", "Segoe UI", sans-serif;
      overflow: hidden;
    }
    .welcome, .ready {
      transition: opacity 0.7s ease, transform 0.7s ease;
    }
    .welcome {
      position: fixed;
      inset: 0;
      display: grid;
      place-items: center;
      text-align: center;
      padding: 32px;
      z-index: 2;
    }
    .welcome h1 { margin: 18px 0 8px; font-size: 42px; font-weight: 700; letter-spacing: -0.03em; }
    .welcome p { margin: 0; color: #b7becb; font-size: 16px; font-weight: 500; }
    .welcome .logo {
      width: 96px;
      height: auto;
      filter: drop-shadow(0 0 16px rgba(255, 255, 255, 0.55)) drop-shadow(0 0 36px rgba(170, 190, 255, 0.45));
    }
    .ready {
      width: min(420px, calc(100% - 48px));
      opacity: 0;
      transform: translateY(22px);
      pointer-events: none;
    }
    body.show-ready .welcome { opacity: 0; transform: translateY(-18px); pointer-events: none; }
    body.show-ready .ready { opacity: 1; transform: none; pointer-events: auto; }
    .card {
      background: #14161e;
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 22px;
      padding: 36px 28px 28px;
      text-align: center;
      box-shadow: 0 24px 64px rgba(0, 0, 0, 0.45);
    }
    .card .logo {
      width: 72px;
      height: auto;
      filter: drop-shadow(0 0 14px rgba(255, 255, 255, 0.4));
    }
    .card h1 { margin: 14px 0 20px; font-size: 34px; font-weight: 700; letter-spacing: -0.03em; }
    .avatar {
      width: 88px;
      height: 88px;
      border-radius: 999px;
      object-fit: cover;
      background: #1c1f28;
    }
    .name { margin: 12px 0 22px; font-size: 18px; font-weight: 650; }
    button {
      width: 100%;
      height: 48px;
      border: 0;
      border-radius: 12px;
      background: #2a2e38;
      color: white;
      font: inherit;
      font-weight: 700;
      font-size: 16px;
      cursor: pointer;
    }
    button:hover { background: #353a46; }
  </style>
</head>
<body>
  <section class="welcome">
    <div>
      ${logoTag}
      <h1>Welcome back</h1>
      <p>Glad to see you again, ${safeName}</p>
    </div>
  </section>
  <section class="ready">
    <div class="card">
      ${logoTag}
      <h1>Ready to Play?</h1>
      <img class="avatar" src="${safeAvatar}" alt="" />
      <p class="name">${safeName}</p>
      <form id="go">
        <button type="submit">Continue</button>
      </form>
    </div>
  </section>
  <script>
    const state = ${safeState};
    setTimeout(() => document.body.classList.add('show-ready'), 1400);
    document.getElementById('go').addEventListener('submit', async (event) => {
      event.preventDefault();
      const button = event.currentTarget.querySelector('button');
      if (button.dataset.sent === '1') return;
      button.dataset.sent = '1';
      button.textContent = 'Continue';
      try {
        await fetch('/callback/continue', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ state }),
        });
      } catch { /* the launcher still polls */ }
      button.dataset.sent = '0';
      button.textContent = 'Continue';
    });
  </script>
</body>
</html>`;
}

function renderMessage(title, detail) {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>Nexa</title>
  <style>
    body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #07080c; color: #f5f7fb; font-family: "Segoe UI", sans-serif; }
    main { width: min(440px, calc(100% - 48px)); text-align: center; }
    h1 { font-size: 32px; margin: 0 0 12px; }
    p { color: #c5cad6; line-height: 1.5; }
  </style>
</head>
<body>
  <main>
    <h1>${escapeHtml(title)}</h1>
    <p>${escapeHtml(detail)}</p>
  </main>
</body>
</html>`;
}

function renderWaitingPage(state) {
  const safeState = JSON.stringify(state);
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>Nexa</title>
  <style>
    body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #07080c; color: #f5f7fb; font-family: "Segoe UI", sans-serif; }
    main { width: min(460px, calc(100% - 48px)); text-align: center; }
    h1 { font-size: 32px; margin: 0 0 12px; }
    p { color: #c5cad6; line-height: 1.5; }
    code { color: #f5f7fb; }
  </style>
</head>
<body>
  <main>
    <h1>Waiting for Discord</h1>
    <p>The Nexa bot is the test path for this sign-in. In Discord, run <code>/login</code>. This page then shows Ready to Play with your Discord name and avatar.</p>
  </main>
  <script>
    const state = ${safeState};
    async function poll() {
      try {
        const response = await fetch('/callback/status?state=' + encodeURIComponent(state));
        const data = await response.json();
        if (data.ready) location.replace('/callback/ready?state=' + encodeURIComponent(state));
      } catch { /* keep waiting */ }
    }
    setInterval(poll, 1000);
    poll();
  </script>
</body>
</html>`;
}

function sendHtml(res, status, html) {
  res.writeHead(status, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(html);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', (chunk) => chunks.push(chunk));
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

async function onCallback(req, res) {
  const url = new URL(req.url || '/', DISCORD_REDIRECT_URI);
  if (req.method === 'GET' && url.pathname === '/callback/status') {
    const state = url.searchParams.get('state') || '';
    const row = pending.get(state);
    const ready = Boolean(row && row.status === 'authorized' && row.profile);
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify({ ready }));
    return;
  }

  if (req.method === 'GET' && url.pathname === '/callback/ready') {
    const state = url.searchParams.get('state') || '';
    const row = pending.get(state);
    if (!row?.profile || row.status !== 'authorized') {
      sendHtml(res, 400, renderMessage('Sign-in expired', 'Start again from Continue with Discord in Nexa.'));
      return;
    }
    sendHtml(res, 200, renderReadyPage({ username: row.profile.username, avatarUrl: row.profile.avatarUrl, state }));
    return;
  }

  if (req.method === 'GET' && url.pathname === '/callback') {
    const oauthError = url.searchParams.get('error');
    const state = url.searchParams.get('state') || '';
    const row = pending.get(state);
    if (oauthError) {
      const message = 'Discord did not finish sign-in. Return to Nexa and try again.';
      if (row) {
        row.status = 'error';
        row.error = message;
      }
      sendHtml(res, 400, renderMessage('Sign-in stopped', message));
      return;
    }
    const code = url.searchParams.get('code') || '';
    if (!row || !code) {
      sendHtml(res, 400, renderMessage('Sign-in expired', 'Start again from Continue with Discord in Nexa.'));
      return;
    }
    let profile = row.profile || null;
    try {
      const accessToken = await exchangeCode(code, row.verifier);
      profile = await fetchIdentity(accessToken);
    } catch {
      profile = profile || (await identityFromGuild(url.searchParams.get('guild_id') || ''));
    }
    if (!profile?.id || !profile?.username) {
      row.status = 'needs-bot';
      row.verifier = '';
      sendHtml(res, 200, renderWaitingPage(state));
      return;
    }
    row.profile = {
      id: profile.id,
      username: profile.username,
      avatarUrl: profile.avatarUrl || avatarUrl({ id: profile.id, avatar: null }),
    };
    row.status = 'authorized';
    row.verifier = '';
    sendHtml(res, 200, renderReadyPage({ username: row.profile.username, avatarUrl: row.profile.avatarUrl, state }));
    return;
  }

  if (req.method === 'POST' && url.pathname === '/callback/continue') {
    const raw = await readBody(req);
    const type = String(req.headers['content-type'] || '');
    let state = '';
    if (type.includes('application/json')) {
      try {
        state = String(JSON.parse(raw).state || '');
      } catch {
        state = '';
      }
    } else {
      state = new URLSearchParams(raw).get('state') || '';
    }
    const row = pending.get(state);
    if (!row || row.status !== 'authorized' || !row.profile) {
      res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
      res.end(JSON.stringify({ ok: false }));
      return;
    }
    row.token = await applyProfile(row.profile);
    row.status = 'ready';
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify({ ok: true }));
    return;
  }

  sendHtml(res, 404, renderMessage('Not found', 'This address only finishes Discord sign-in.'));
}

export function startDiscordCallback() {
  if (callbackListening) return;
  const server = http.createServer((req, res) => {
    onCallback(req, res).catch((error) => {
      const message = error instanceof Error ? error.message : 'Discord sign-in failed.';
      sendHtml(res, 500, renderMessage('Discord sign-in failed', message));
    });
  });
  server.on('error', (error) => {
    callbackListening = false;
    console.error(`[nexa-oauth] ${DISCORD_REDIRECT_URI} failed: ${error.message}`);
  });
  server.listen(4390, '127.0.0.1', () => {
    callbackListening = true;
    console.log(`[nexa-oauth] listening on ${DISCORD_REDIRECT_URI}`);
  });
}
