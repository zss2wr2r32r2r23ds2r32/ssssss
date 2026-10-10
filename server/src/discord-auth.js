import crypto from 'node:crypto';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { loadEnv } from './env.js';
import { discordUserIsAdmin } from './roles.js';
import { emptyStats, getDb, save } from './store.js';

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
  if (!same) db.user.stats = emptyStats();
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

function bannerFile() {
  const candidates = [
    process.env.NEXA_STATIC ? path.join(process.env.NEXA_STATIC, 'banner-login.png') : '',
    path.join(process.cwd(), 'launcher', 'public', 'banner-login.png'),
    path.join(process.cwd(), '..', 'launcher', 'public', 'banner-login.png'),
  ].filter(Boolean);
  return candidates.find((file) => fs.existsSync(file)) || '';
}

function truncateDiscordId(id) {
  const value = String(id || '');
  if (value.length <= 12) return value;
  return `${value.slice(0, 6)}…${value.slice(-4)}`;
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

export function renderReadyPage({ username, avatarUrl: picture, state, discordId }) {
  const font = fontDataUrl();
  const safeName = escapeHtml(username);
  const safeAvatar = escapeHtml(picture);
  const safeId = escapeHtml(truncateDiscordId(discordId));
  const safeState = JSON.stringify(state);
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
    .signin, .greet, .queue {
      position: fixed;
      inset: 0;
      display: grid;
      place-items: center;
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.55s ease;
    }
    .signin { opacity: 1; pointer-events: auto; background: #07080c; }
    body.show-greet .signin { opacity: 0; pointer-events: none; }
    body.show-greet .greet { opacity: 1; }
    body.show-queue .greet { opacity: 0; }
    body.show-queue .queue { opacity: 1; pointer-events: auto; }
    .signin-wrap { width: min(440px, calc(100% - 40px)); text-align: center; }
    .signin h1 { margin: 0 0 8px; font-size: 44px; font-weight: 750; letter-spacing: -0.04em; }
    .subtitle { margin: 0 0 22px; color: #9aa3b5; font-size: 15px; }
    .card {
      background: #16181f;
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 18px;
      padding: 26px 22px 18px;
      box-shadow: 0 24px 60px rgba(0, 0, 0, 0.35);
    }
    .kicker { margin: 0 0 14px; color: #9aa3b5; font-size: 13px; }
    .avatar {
      width: 84px;
      height: 84px;
      border-radius: 999px;
      object-fit: cover;
      background: #1c1f28;
    }
    .username { margin: 12px 0 2px; font-size: 20px; font-weight: 750; }
    .discord-id { margin: 0 0 12px; color: #8b93a7; font-size: 13px; }
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: #2f9e57;
      color: white;
      border-radius: 999px;
      padding: 4px 10px 4px 6px;
      font-size: 12px;
      font-weight: 700;
    }
    .chip svg { width: 16px; height: 16px; display: block; }
    button {
      width: 100%;
      height: 46px;
      margin-top: 18px;
      border: 0;
      border-radius: 10px;
      background: #fff;
      color: #111318;
      font: inherit;
      font-weight: 750;
      font-size: 15px;
      cursor: pointer;
    }
    button:hover { background: #f2f4f8; }
    .secure {
      margin: 14px 0 0;
      color: #8b93a7;
      font-size: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
    }
    .greet { background: #07080c; overflow: hidden; }
    .greet-bg {
      position: absolute;
      inset: -48px;
      background: #07080c url('/callback/banner') center / cover no-repeat;
      filter: blur(18px);
      transform: scale(1.08);
    }
    .greet-shade { position: absolute; inset: 0; background: rgba(5, 6, 10, 0.42); }
    .greet-copy { position: relative; z-index: 1; text-align: center; }
    .greet .avatar { width: 112px; height: 112px; border: 3px solid rgba(255, 255, 255, 0.9); }
    .greet h1 { margin: 16px 0 12px; font-size: 40px; font-weight: 750; letter-spacing: -0.03em; }
    .accent-line { width: 72px; height: 3px; margin: 0 auto; border-radius: 999px; background: #d7dbe4; }
    .qcard {
      width: min(440px, calc(100% - 48px));
      background: #12141c;
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 18px;
      padding: 28px 24px 22px;
      text-align: center;
      box-shadow: 0 24px 60px rgba(0, 0, 0, 0.4);
    }
    .qcard .logo { width: 42px; height: auto; }
    .word { margin: 8px 0 18px; font-size: 28px; font-weight: 800; letter-spacing: 0.16em; }
    .status { margin: 0 0 14px; color: #d5dbe8; font-size: 15px; }
    .bar { height: 8px; border-radius: 999px; background: #2a2e38; overflow: hidden; }
    .bar > div { height: 100%; width: 0; background: linear-gradient(90deg, #8ea0ff, #f5f7fb); }
    .stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 18px; }
    .stats strong { display: block; font-size: 18px; }
    .stats span { color: #8b93a7; font-size: 12px; }
    .note { margin: 16px 0 0; color: #8b93a7; font-size: 13px; }
  </style>
</head>
<body>
  <section class="signin">
    <div class="signin-wrap">
      <h1>Nexa</h1>
      <p class="subtitle">Sign in to the launcher with your Discord account.</p>
      <div class="card">
        <p class="kicker">Signing in as</p>
        <img class="avatar" src="${safeAvatar}" alt="" />
        <p class="username">${safeName}</p>
        <p class="discord-id">${safeId}</p>
        <span class="chip">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M19.3 5.3A16 16 0 0 0 15.9 4l-.4.8a13.5 13.5 0 0 1 4 1.5 14 14 0 0 0-13 0A12 12 0 0 1 8.5 4L8.1 4A16 16 0 0 0 4.7 5.3C1.7 9.8 1 13.4 1.4 16.9A16 16 0 0 0 6.3 19l.8-1.2a11 11 0 0 1-1.6-.8l.4-.3c3.2 1.5 6.6 1.5 9.8 0l.4.3c-.5.3-1 .6-1.6.8l.8 1.2a16 16 0 0 0 4.9-2.1c.5-4.1-.7-7.6-3.3-11.6ZM8.8 14.6c-1 0-1.8-.9-1.8-2s.8-2 1.8-2 1.8.9 1.8 2-.8 2-1.8 2Zm6.4 0c-1 0-1.8-.9-1.8-2s.8-2 1.8-2 1.8.9 1.8 2-.8 2-1.8 2Z"/></svg>
          Discord
        </span>
        <form id="go">
          <button type="submit">Continue as ${safeName}</button>
        </form>
        <p class="secure">
          <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden="true"><path fill="none" stroke="currentColor" stroke-width="1.8" d="M7 10V7a5 5 0 0 1 10 0v3"/><rect x="5" y="10" width="14" height="10" rx="2" fill="none" stroke="currentColor" stroke-width="1.8"/></svg>
          Secure Discord authentication
        </p>
      </div>
    </div>
  </section>
  <script>
    const state = ${safeState};
    const button = document.querySelector('#go button');
    const label = button.textContent;
    document.getElementById('go').addEventListener('submit', async (event) => {
      event.preventDefault();
      if (document.body.dataset.left === '1') return;
      document.body.dataset.left = '1';
      button.disabled = true;
      button.textContent = 'Opening Nexa…';
      try {
        const response = await fetch('/callback/continue', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ state }),
        });
        if (!response.ok) throw new Error('continue');
        button.textContent = 'Continue in the Nexa window';
      } catch {
        document.body.dataset.left = '0';
        button.disabled = false;
        button.textContent = label;
      }
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
  if (req.method === 'GET' && url.pathname === '/callback/banner') {
    const file = bannerFile();
    if (!file) {
      res.writeHead(404);
      res.end();
      return;
    }
    res.writeHead(200, { 'Content-Type': 'image/png', 'Cache-Control': 'no-store' });
    fs.createReadStream(file).pipe(res);
    return;
  }
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
    sendHtml(res, 200, renderReadyPage({ username: row.profile.username, avatarUrl: row.profile.avatarUrl, discordId: row.profile.id, state }));
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
    sendHtml(res, 200, renderReadyPage({ username: row.profile.username, avatarUrl: row.profile.avatarUrl, discordId: row.profile.id, state }));
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
