# Nautical Fortnite Launcher

Premium Windows desktop utility for competitive Fortnite sessions. Dark nautical aesthetic, profile-based setup, and a secure Electron shell.

Nautical is a **normal Windows application**. It uses documented OS and Epic config-file APIs only. It does **not** inject into Fortnite, read game memory, touch packets, bypass Easy Anti-Cheat, automate aim/recoil, or collect credentials.

## Features

- **Home** — install status, active profile, stretched/display resolution, crosshair + macro state, large **LAUNCH FORTNITE**
- **Launch sequence** — refuse a second session if Fortnite is already running → apply active profile Fortnite-only config → optional safe cleanup → start the configured executable → start overlay/macro when enabled → monitor the process → on close/crash stop overlay + macro and restore temporary display/config
- **Path detection** — searches common Epic/Fortnite locations and launcher manifests; browse + validate `Fortnite.exe` / `FortniteClient-Win64-Shipping.exe`; persist JSON
- **Crosshair overlay** — always-on-top, click-through, centered on the primary display, exclude-from-capture when Windows allows it
- **Resolution** — example presets, custom apply/test, native backup, Fortnite `GameUserSettings.ini` backup, GPU scaling notes (not FOV, not “best res”)
- **Performance** — Epic-cited PC competitive helpers + optional cleanup with a hard whitelist
- **Macro** — interval key/mouse repeat with focus gate and a prominent Epic rules warning
- **Profiles** — Competitive / Aggressive / Native seeds, CRUD, duplicate, default
- **First-run wizard** — Welcome to Nautical → detect → install → monitor → resolution → crosshair → Home

Version **1.0.0**.

## Security model

| Surface | Behavior |
| --- | --- |
| Renderer | `contextIsolation: true`, `nodeIntegration: false`, `sandbox: true` |
| Preload | Typed `contextBridge` only — no `fs`, `shell`, or `child_process` |
| IPC | Channel whitelist + Zod validation on every payload |
| External links | HTTPS Discord hosts only |
| Windows work | Main process modules only |
| Forbidden | Memory/packet/anti-cheat/aim/recoil/bot/injection/ESP/wallhacks/credentials |

Config lives in the Electron user-data folder as pretty-printed `nautical-config.json`.

## Requirements

- Node.js 20+
- Windows 10/11 for display changes, process cleanup, overlay capture exclusion, and Fortnite launch
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
- Cleanup frees background CPU/RAM. It is **not** an FPS guarantee. System, drivers, security, GPU, audio, Explorer, Fortnite, and Epic auth are never killed.
- Macros may violate Epic rules. The control stays off until you acknowledge that.
- Windows settings are temporary unless you explicitly save. Emergency **Restore Native** is always available.

## License

MIT
