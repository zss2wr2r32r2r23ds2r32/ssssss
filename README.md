# Pine Pollen Macro

Bee Swarm Simulator collector for **Pine Tree** field. Travel, gather, and convert loops are modeled on [Natro Macro](https://github.com/NatroTeam/NatroMacro) (GPL-3.0): same hive-to-ramp math, Pine Tree cannon/walk routes, and `4000 / movespeed` tile timing.

This build is Pine-only and gated behind a license key.

## Testing license key

```
admintest123
```

That admin testing key is hashed in `licenses.json`. Type it exactly (lowercase, no spaces).

## Windows (the real macro)

1. Install [AutoHotkey v2](https://www.autohotkey.com/).
2. Open Bee Swarm Simulator, claim a hive, and stand on your pad.
3. Double-click `START.bat` (or run `pine_macro.ahk`).
4. Enter a license key (`admintest123` for testing).
5. Set **Hive slot** and **Move speed** to match your hive and unhasted move speed.
6. Press **F1** / Start.

| Hotkey | Action |
| --- | --- |
| F1 | Start |
| F2 | Pause / resume |
| F3 | Stop |

Travel can be **Walk** (safer, slower) or **Cannon** (Natro pine glider). Default gather pattern is **CornerXSnake**, Natro's Pine Tree default.

The AutoHotkey script sends keyboard input to the Roblox window. It does not inject into the client or read game memory.

## Simulator and tests (any OS)

Preview the license gate and one collection cycle without Roblox:

```bash
python3 simulator/pine_collector_sim.py
```

```bash
python3 -m unittest discover -s tests -v
python3 tools/check_license.py admintest123
```

Add another hashed key:

```bash
python3 tools/add_license.py --key YOURKEY --label "Friend" --role user
```

## Layout

- `pine_macro.ahk` — Windows collector GUI and loop
- `lib/License.ahk` — SHA-256 license check (same salt/hash as Python)
- `paths/pinetree.ahk` — Pine Tree go-to / walk-from routes
- `licenses.json` — hashed allowlist
- `pine_core/` — shared license, path, and loop logic

## License

GNU GPL v3.0. Pine routes and walk timing are adapted from Natro Macro. If you distribute this project you must keep the GPL notice and source available.
