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

`npm run dev` starts the API and the launcher together. **Continue with Discord** opens one browser window for Discord OAuth. After you authorize, that same window shows Welcome back, then Ready to Play. The launcher signs in when you press Continue.

Profile, stats, shop, news, builds, leaderboard, and settings live in `server/data/db.json` (gitignored). Delete that file to reseed.

## Discord bot and OAuth

Nexa signs in with Discord application `1558290151124369440`. Create or open that application in the [Discord Developer Portal](https://discord.com/developers/applications).

Add this redirect URI and no other:

```
http://127.0.0.1:4390/callback
```

That same address is the Ready to Play page for `npm run dev` and for `Nexa.exe`. The launcher uses PKCE. The client id is public. Do not put a client secret in the repo or in the exe.

Continue with Discord uses this authorize URL, plus `state` and a PKCE `code_challenge`. The scope is `identify` only. It does not include `bot` or a permissions value, so Discord does not ask you to add a server.

```
https://discord.com/oauth2/authorize?client_id=1558290151124369440&response_type=code&redirect_uri=http%3A%2F%2F127.0.0.1%3A4390%2Fcallback&scope=identify
```

Install the bot once, from this invite, not from the login button:

```
https://discord.com/oauth2/authorize?client_id=1558290151124369440&permissions=8&scope=bot&integration_type=0
```

| What | Where |
| --- | --- |
| Bot token | `bot/.env` as `DISCORD_TOKEN` |
| Application id for slash commands | `bot/.env` as `DISCORD_CLIENT_ID` |
| Client secret, only if Discord rejects the PKCE exchange | environment variable `DISCORD_CLIENT_SECRET`, or `server/.env` (see `server/.env.example`) |
| Discord role id for shop and news controls | `ADMIN_ROLE_ID` in `bot/.env` |

The bot looks up `ADMIN_ROLE_ID` on the signed-in Discord user. Add item, remove item, add news, and remove news are shown only when that user has the role. If `ADMIN_ROLE_ID` is missing, or the bot is not running, those controls stay hidden. Nexa does not treat every signed-in user as an admin. Copy `bot/.env.example` to `bot/.env` and put the bot token there. From the repo root, with the API already running:

```bash
npm run bot
```

The bot lives in `bot/`. There is no separate bot executable. If `DISCORD_TOKEN` is missing, the bot logs that and exits. The launcher keeps running.

`LAUNCHER_API_URL` defaults to `http://127.0.0.1:4177`.

**Sign in with the bot**

1. `npm run dev`
2. `npm run bot`
3. In Nexa, click **Continue with Discord** and approve identify access for application `1558290151124369440`. Stay in that browser window.
4. The page shows Welcome back, then fades into Ready to Play. Continue stays labeled Continue.
5. Press **Continue**. Nexa replaces any previous local session with that Discord user. Settings shows that username and avatar.

If the code exchange cannot read the Discord user, the same window waits and `/login` can finish the profile. A fake placeholder account is not kept after this login.

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

The home greeting uses the visible name. The pencil opens a display-name dialog. A library folder has to contain both `FortniteGame` and `Engine`. Nexa launches `FortniteClient-Win64-Shipping.exe` (usually `FortniteGame/Binaries/Win64/FortniteClient-Win64-Shipping.exe`) from the card and from Launch Fortnite, with the working directory set to the folder that contains that exe. It does not start EpicGamesLauncher.exe and it does not pass `-noeac`. If `splash.bmp` is in that folder, the card uses it at the same aspect, scaled down. Nexa does not download game files.

The item shop refreshes every day at 01:00 Europe/London from the [Fortnite cosmetics API](https://fortnite-api.com/v2/cosmetics/br). Featured and Daily are a random selection, and the images are the API image URLs. The countdown chip stays. An imported shop image is stored as provided. The card supplies the rarity frame, name, type, and price.

## Updates

Settings → Launcher → **Check for updates** reads GitHub releases for `zss2wr2r32r2r23ds2r32/ssssss`. The tag it expects is `nexa-` plus the version, for example `nexa-0.1.3`. It uses the highest `nexa-` release, not an older tag. The release asset must be named `Nexa.exe`. When you are already on that version, the button does not start another process. It shows a dismissable toast: You’re on the latest version. When a newer tag exists, Nexa downloads `Nexa.exe` in the app and a hidden helper swaps the file, then Nexa restarts. That path does not open a command prompt.

## Layout

- `launcher/` — Vite, React, TypeScript. Plus Jakarta Sans. Tab changes fade and slide.
- `electron/` — desktop window.
- `server/` — Express. JSON file in `server/data/`.
- `bot/` — discord.js.

Accent color, the in-game name, and the game toggles (Mobile builds, Reset on Release, Potato Graphics) are stored as settings. They do not edit a game install.
