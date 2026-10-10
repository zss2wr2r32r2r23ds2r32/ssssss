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

Shop add/remove and news posts stay limited to those admin ids. Copy `bot/.env.example` to `bot/.env` and put the bot token there. From the repo root:

```bash
npm run bot
```

The bot lives in `bot/`. There is no separate bot executable. If `DISCORD_TOKEN` is missing, the bot logs that and exits. The launcher keeps running.

`LAUNCHER_API_URL` defaults to `http://127.0.0.1:4177`. The bot only calls GET endpoints.

**Where commands are registered**

- Set `DISCORD_GUILD_ID` to a server id to register `/stats`, `/shop`, and `/link` on that server right away.
- Leave `DISCORD_GUILD_ID` empty to register **global** commands. Those can take up to an hour to appear.

| Command | What it does |
| --- | --- |
| `/stats` | Eliminations, wins, matches, and V-Bucks for the local profile |
| `/shop` | Time until 01:00 Europe/London, plus a few item names and prices |
| `/link` | Acknowledges the signed-in profile as linked |

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

The home greeting uses the Discord username. The pencil opens a display-name dialog. Library cards come from a folder that already contains `FortniteShipping.exe`. Nexa does not download game files.

## Layout

- `launcher/` — Vite, React, TypeScript. Plus Jakarta Sans. Tab changes fade and slide.
- `electron/` — desktop window.
- `server/` — Express. JSON file in `server/data/`.
- `bot/` — discord.js.

Accent color, the in-game name, and the game toggles (Mobile builds, Reset on Release, Potato Graphics) are stored as settings. They do not edit a game install.
