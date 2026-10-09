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

`npm run dev` starts the API and the launcher together. Sign in with **Continue with Discord** — that logs in the local admin immediately (no OAuth yet).

The admin profile is **Avix** (`100000000000000001`). Profile, stats, shop, news, builds, leaderboard, and settings live in `server/data/db.json` (gitignored). Delete that file to reseed.

## Discord bot

The bot is optional. If `DISCORD_TOKEN` is missing it logs a clear message and exits without taking the launcher down.

```bash
cp bot/.env.example bot/.env
# fill in DISCORD_TOKEN and DISCORD_CLIENT_ID
npm run bot
```

`LAUNCHER_API_URL` defaults to `http://127.0.0.1:4177`. The bot only calls GET endpoints.

Invite the app with the `bot` and `applications.commands` scopes (no extra permissions):

```
https://discord.com/oauth2/authorize?client_id=YOUR_CLIENT_ID&scope=bot%20applications.commands&permissions=0
```

**Where commands are registered**

- Set `DISCORD_GUILD_ID` to a server id to register `/stats`, `/shop`, and `/link` on that server right away.
- Leave `DISCORD_GUILD_ID` empty to register **global** commands. Those can take up to an hour to appear.

| Command | What it does |
| --- | --- |
| `/stats` | Eliminations, wins, matches, and V-Bucks for the local profile |
| `/shop` | Time until 01:00 Europe/London, plus a few item names and prices |
| `/link` | Acknowledges the local admin profile as linked |

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

`npm run pack:win` produces `release/Nexa.exe`. That file is not committed. The window title is Nexa and it uses the Nexa logo. The packaged app starts the local API itself and opens the same UI. In the browser, the title-bar buttons do nothing; in the Electron window they minimize, maximize, and close.

The home greeting uses the Discord username. The pencil in Settings changes the in-game name only.

## Layout

- `launcher/` — Vite, React, TypeScript. Plus Jakarta Sans. Tab changes fade and slide.
- `electron/` — desktop window.
- `server/` — Express. JSON file in `server/data/`.
- `bot/` — discord.js.

Accent color, the in-game name, and the game toggles (Mobile builds, Reset on Release, Potato Graphics) are stored as settings. They do not edit a game install.
