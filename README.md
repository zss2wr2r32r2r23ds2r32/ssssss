# Nexa

Nexa is a desktop-style launcher (a web app framed like a native window) plus a small Discord bot. Both talk to a local API. Library “downloads” only save a season label or a folder path you already have. Nexa does not download, host, or patch game files, and it does not touch anti-cheat.

## Run

Requires Node.js 20+.

```bash
npm install
npm run dev
```

| Process | URL |
| --- | --- |
| Launcher | http://localhost:5173 |
| API | http://127.0.0.1:4177 |

`npm run dev` starts the API and the launcher together. **Continue with Discord** opens the system browser for Discord OAuth. The launcher window signs in after you press Continue on the Ready to Play page.

Profile, stats, shop, news, builds, leaderboard, and settings live in `server/data/db.json` (gitignored). Delete that file to reseed.

## Discord bot and OAuth

Nexa signs in with Discord application `1558290151124369440`. Create or open that application in the [Discord Developer Portal](https://discord.com/developers/applications).

Add this redirect URI and no other:

```
http://127.0.0.1:4390/callback
```

That same address is the Ready to Play page for `npm run dev` and for `Nexa.exe`. The launcher uses PKCE. The client id is public. Do not put a client secret in the repo or in the exe.

| What | Where |
| --- | --- |
| Bot token | `bot/.env` as `DISCORD_TOKEN` |
| Application id for slash commands | `bot/.env` as `DISCORD_CLIENT_ID` |
| Client secret, only if Discord rejects the PKCE exchange | environment variable `DISCORD_CLIENT_SECRET`, or `server/.env` (see `server/.env.example`) |
| Admin Discord user ids, comma-separated | `ADMIN_DISCORD_IDS` in `server/.env` |

If `ADMIN_DISCORD_IDS` is empty, the Discord user who finishes sign-in is an admin and can import shop items. If it lists ids, only those accounts are admins. Copy `bot/.env.example` to `bot/.env` and put the bot token there. From the repo root, with the API already running:

```bash
npm run bot
```

The bot lives in `bot/`. There is no separate bot executable. If `DISCORD_TOKEN` is missing, the bot logs that and exits. The launcher keeps running.

`LAUNCHER_API_URL` defaults to `http://127.0.0.1:4177`.

**Sign in with the bot**

1. `npm run dev`
2. `npm run bot`
3. In Nexa, click **Continue with Discord** and approve the browser prompt for application `1558290151124369440`.
4. If Ready to Play does not already show your Discord name and avatar, run `/login` in that Discord server.
5. Press **Continue** on Ready to Play. Nexa replaces any previous local session with that Discord user. Settings shows that username and avatar.

The bot token in `bot/.env` is also how the callback can match a bot invite from the last few minutes. A fake placeholder account is not kept after this login.

**Where commands are registered**

- Set `DISCORD_GUILD_ID` to a server id to register `/login`, `/stats`, `/shop`, and `/link` on that server right away.
- Leave `DISCORD_GUILD_ID` empty to register **global** commands. Those can take up to an hour to appear.

| Command | What it does |
| --- | --- |
| `/login` | Sends your Discord username and avatar to a sign-in that Nexa already started with Continue with Discord |
| `/stats` | Eliminations, wins, matches, and V-Bucks for the signed-in profile |
| `/shop` | Time until 01:00 Europe/London, plus a few item names and prices |
| `/link` | Shows the Discord name and id currently signed in to Nexa |

## Scripts

| Script | Purpose |
| --- | --- |
| `npm run dev` | API (4177) + launcher (5173) in the browser |
| `npm start` | API only |
| `npm run bot` | Discord bot |
| `npm run build` | Typecheck and build the launcher |
| `npm run desktop` | Open the Electron window (start `npm run dev` first) |
| `npm run pack:win` | Build a portable Windows `Nexa.exe` into `release/` |

## Windows app

`npm run pack:win` produces `release/Nexa.exe`. That file is not committed. The window title is Nexa and it uses the Nexa logo. The packaged app loads the API inside the Electron process before the window opens, then the UI calls that local server. Continue with Discord does not need a separate `npm run dev`. In the browser, the title-bar buttons do nothing; in the Electron window they minimize, maximize, and close.

The home greeting uses the Discord username. The pencil opens a display-name dialog. A library folder has to contain both `FortniteGame` and `Engine`. Nexa launches `FortniteClient-Win64-Shipping.exe` (usually `FortniteGame/Binaries/Win64/FortniteClient-Win64-Shipping.exe`) from the card and from Launch Fortnite. If `splash.bmp` is in that folder, the card uses it. Nexa does not download game files.

## Updates

Settings → Launcher → **Check for updates** reads GitHub releases for `zss2wr2r32r2r23ds2r32/ssssss`. The tag it expects is `nexa-` plus the version, for example `nexa-0.1.1`. The release asset must be named `Nexa.exe`. The button compares that tag with the running version. A newer tag downloads `Nexa.exe`, replaces the portable app, and restarts it. The same version says you're on the latest.

## Layout

- `launcher/` — Vite, React, TypeScript. Plus Jakarta Sans. Tab changes fade and slide.
- `electron/` — desktop window.
- `server/` — Express. JSON file in `server/data/`.
- `bot/` — discord.js.

Accent color, the in-game name, and the game toggles (Mobile builds, Reset on Release, Potato Graphics) are stored as settings. They do not edit a game install.
