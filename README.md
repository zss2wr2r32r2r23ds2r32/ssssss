# Avix Launcher

Windows desktop utility for competitive Fortnite sessions. Dark navy/purple shell, magenta accents, and a secure Electron host.

Avix is a **normal Windows application**. It uses documented OS and Epic config-file APIs only. It does **not** inject into Fortnite, read game memory, touch packets, bypass Easy Anti-Cheat, automate aim/recoil, or collect credentials.

## Features

- **Home** — Believer Beach hero, user profile picture stored in app userData, install detect/browse, large **LAUNCH FORTNITE**
- **Launch sequence** — prefer Epic Games Launcher URI / FortniteBootstrapper (Shipping.exe alone often exits without an Epic session) → wait until `FortniteClient-Win64-Shipping.exe` is actually running before showing Running → start overlay/macro when enabled → restore temporary display/config on a real session close
- **Path detection** — Epic `.item` manifests + `LauncherInstalled.dat`; prefers `FortniteClient-Win64-Shipping.exe` → `FortniteBootstrapper.exe` → `Fortnite.exe`
- **Crosshair overlay** — always-on-top, click-through, centered on the primary display, exclude-from-capture when Windows allows it
- **Resolution** — example presets, custom apply/test, native backup, Fortnite `GameUserSettings.ini` backup, GPU scaling notes (not FOV, not “best res”)
- **Performance** — live CPU, RAM, and GPU usage from OS counters, plus Avix’s own process footprint. No FPS claims and no “close Discord/Chrome” cleanup on this page
- **Macro** — interval key/mouse/scroll-wheel repeat with focus gate and a prominent Epic rules warning
- **Scrim alert** — enable per org/format (Noble Elite, Poyo Elite, Ladder Elite, Duos Elite, Solos Elite, plus custom). In-app toast plus an optional Discord webhook you configure. v1 polls optional HTTPS status URLs (`{ "live": true }` or the word `live`) or fires a manual Test — there is no official Epic/org API
- **Profiles** — Competitive / Aggressive / Native seeds, CRUD, duplicate, default
- **First-run wizard** — Welcome to Avix → detect → install → monitor → resolution → crosshair → Home

Version **1.0.0**.

## Security model

| Surface | Behavior |
| --- | --- |
| Renderer | `contextIsolation: true`, `nodeIntegration: false`, `sandbox: true` |
| Preload | Typed `contextBridge` only — no `fs`, `shell`, or `child_process` |
| IPC | Channel whitelist + Zod validation on every payload |
| External links | HTTPS Discord hosts only |
| Webhooks | User-supplied `discord.com/api/webhooks/…` URL stored locally |
| Windows work | Main process modules only |
| Forbidden | Memory/packet/anti-cheat/aim/recoil/bot/injection/ESP/wallhacks/credentials |

Config lives in the Electron user-data folder as pretty-printed `avix-config.json`. Older `nautical-config.json` files are migrated on first launch.

The renderer preload API remains `window.nautical` for compatibility.

## Requirements

- Node.js 20+
- Windows 10/11 for display changes, overlay capture exclusion, and Fortnite launch
- Linux/macOS can run the UI and unit tests; Windows-specific actions degrade gracefully

## Scripts

```bash
npm install          # install dependencies
npm run dev          # Electron + Vite development
npm run ui           # renderer-only browser preview (mock bridge)
npm run typecheck    # TypeScript (main + renderer)
npm test             # unit tests
npm run build        # compile main, preload, renderer
npm run dist         # Windows NSIS installer + portable .exe
npm run dist:dir     # unpacked Windows dir (faster local check)
npm run dist:nsis    # NSIS installer only
npm run dist:portable
```

## Packaging

`electron-builder.yml` ships **NSIS** and **portable** x64 Windows targets (`requestedExecutionLevel: asInvoker`). Cross-building from Linux/macOS needs the usual electron-builder toolchain (Wine for NSIS). The Windows machine you play on can always run `npm run dist`.

## Honesty / safety notes

- Resolution examples are **not** claimed to be best. Epic competitive play is **16:9**. Display scaling ≠ FOV.
- Performance meters report OS resource usage. They are **not** an FPS guarantee.
- Last-used skin is read from local Fortnite log files when a `CID_*` cosmetic id appears there. If that is missing, upload a preview image. No memory injection.
- Macros may violate Epic rules. The control stays off until you acknowledge that.
- Windows settings are temporary unless you explicitly save. Emergency **Restore Native** is always available.
- Scrim alerts never scrape Discord without your webhook and never touch Fortnite.

## License

MIT
