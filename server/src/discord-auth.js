import crypto from 'node:crypto';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { loadEnv } from './env.js';
import { getDb, save } from './store.js';

loadEnv();

export const DISCORD_CLIENT_ID = '1558290151124369440';
export const DISCORD_REDIRECT_URI = 'http://127.0.0.1:4390/callback';
const AUTHORIZE_BASE =
  'https://discord.com/oauth2/authorize?client_id=1558290151124369440&permissions=8&response_type=code&redirect_uri=http%3A%2F%2F127.0.0.1%3A4390%2Fcallback&integration_type=0&scope=identify+rpc+bot';

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

function adminIds() {
  return (process.env.ADMIN_DISCORD_IDS || '')
    .split(',')
    .map((id) => id.trim())
    .filter(Boolean);
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

function applyProfile(profile) {
  const db = getDb();
  const same = db.user.discordId === profile.id;
  db.user.id = profile.id;
  db.user.discordId = profile.id;
  db.user.discordName = profile.username;
  db.user.avatar = profile.avatarUrl;
  db.user.role = adminIds().includes(profile.id) ? 'admin' : 'player';
  if (!same) {
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

export function renderReadyPage({ username, avatarUrl: picture, state }) {
  const logo = logoDataUrl();
  const safeName = escapeHtml(username);
  const safeAvatar = escapeHtml(picture);
  const safeState = escapeHtml(state);
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Nexa</title>
  <style>
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
    }
    main { width: min(420px, calc(100% - 48px)); text-align: center; }
    img.logo { width: 72px; height: auto; margin: 0 auto 28px; }
    h1 { margin: 0; font-size: 40px; letter-spacing: -0.03em; font-weight: 700; }
    .avatar {
      width: 96px;
      height: 96px;
      border-radius: 999px;
      object-fit: cover;
      margin: 28px auto 14px;
      background: #141820;
    }
    .name { margin: 0 0 28px; font-size: 18px; color: #c5cad6; font-weight: 600; }
    button {
      width: 100%;
      height: 52px;
      border: 0;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.14);
      color: white;
      font: inherit;
      font-weight: 700;
      font-size: 16px;
      cursor: pointer;
    }
    button:hover { background: rgba(255, 255, 255, 0.24); }
    p.done { color: #c5cad6; line-height: 1.5; }
  </style>
</head>
<body>
  <main>
    ${logo ? `<img class="logo" src="${logo}" alt="Nexa" />` : ''}
    <h1>Ready to Play?</h1>
    <img class="avatar" src="${safeAvatar}" alt="" />
    <p class="name">${safeName}</p>
    <form method="post" action="/callback/continue">
      <input type="hidden" name="state" value="${safeState}" />
      <button type="submit">Continue</button>
    </form>
  </main>
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
    try {
      const accessToken = await exchangeCode(code, row.verifier);
      const profile = await fetchIdentity(accessToken);
      row.profile = profile;
      row.status = 'authorized';
      row.verifier = '';
      sendHtml(res, 200, renderReadyPage({ username: profile.username, avatarUrl: profile.avatarUrl, state }));
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Discord sign-in failed.';
      row.status = 'error';
      row.error = message;
      sendHtml(res, 400, renderMessage('Discord sign-in failed', message));
    }
    return;
  }

  if (req.method === 'POST' && url.pathname === '/callback/continue') {
    const raw = await readBody(req);
    const params = new URLSearchParams(raw);
    const state = params.get('state') || '';
    const row = pending.get(state);
    if (!row || row.status !== 'authorized' || !row.profile) {
      sendHtml(res, 400, renderMessage('Sign-in expired', 'Start again from Continue with Discord in Nexa.'));
      return;
    }
    row.token = applyProfile(row.profile);
    row.status = 'ready';
    sendHtml(res, 200, renderMessage('You’re in', 'Return to the Nexa window. This tab can close.'));
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
