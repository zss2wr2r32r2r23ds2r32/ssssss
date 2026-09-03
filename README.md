# ShardedEventCore

A modular event core for Paper **1.21.11** (Java 21). It runs two gamemodes —
**Crystal** and **DiamondSMP** — from one settings menu, and handles the whole
run: gathering players, the countdown, unlocking PvP, eliminations, the winner
announcement and sending everyone back to the lobby.

Everything visible to a player is configurable, and every feature is a module
that can be turned off at runtime with `/module`.

## Build

```bash
mvn clean package
```

The plugin lands at `target/ShardedEventCore-1.0.0.jar`. A prebuilt copy is
committed at `dist/ShardedEventCore-1.0.0.jar`.

## Quick start

```
/setspawn crystal          # stand where you want spawn; this also turns the whitelist OFF
/settings                  # pick Crystal or DiamondSMP, then set it up
/kit create singleprot     # build the kit in your inventory first, then save it
/spawn all                 # pull everyone to the spawn
/start                     # 10s countdown, then PvP, damage and building unlock
/end                       # whitelist ON, everyone back to /server lobby
```

## Commands

| Command | What it does | Permission |
| --- | --- | --- |
| `/announce <text>` | `ANNOUNCEMENT` title with your text underneath | `shardedcore.announce` |
| `/countdown <seconds\|rest\|stop>` | On-screen countdown; starts the event when it hits zero. Tab completes `3 10 15 rest stop` | `shardedcore.countdown` |
| `/setspawn <crystal\|diasmp>` | Sets that gamemode's spawn and turns the whitelist off | `shardedcore.setspawn` |
| `/spawn [crystal\|diasmp\|all]` | Naming a gamemode sends everyone to its spawn; `all` uses the selected one; no argument moves only you | `shardedcore.spawn` |
| `/settings [crystal\|diasmp]` | Opens the settings menus | `shardedcore.settings` |
| `/kit create\|give\|delete\|list` | `create` snapshots your inventory, armour and offhand | `shardedcore.kit` |
| `/start [now]` | Countdown, then unlock everything and turn the whitelist on | `shardedcore.start` |
| `/end` | Whitelist on, everyone to the lobby | `shardedcore.end` |
| `/module list\|enable\|disable\|toggle\|info\|reload` | Turn features on and off at runtime | `shardedcore.module` |
| `/shardedeventcore reload\|status` | Reload every config file, or print the current state | `shardedcore.admin` |

`shardedcore.bypass` exempts a player from the pre-start lockdown and spawn
protection. It is **not** granted to operators by default, because event hosts
usually play too.

## The settings menus

`/settings` opens **Settings Selector -**, with one icon per gamemode. Left
click selects that gamemode and opens its board; right click releases the
selection. The selected icon glows and swaps to the `selected:` name and lore.

Inside a gamemode's board:

| Icon | Crystal | DiamondSMP | Click |
| --- | --- | --- | --- |
| Sword | Netherite | Diamond | Toggles PvP |
| Spyglass | ✓ | ✓ | Toggles the locator bar |
| Brush | ✓ | ✓ | Toggles spawn protection |
| Helmet | Netherite → kit chooser | Diamond → gives the `diasmp` kit to everyone | Left runs it, right toggles |
| Barrier | ✓ | ✓ | Asks for `<size> [duration]` in chat |
| Totem | ✓ | ✓ | Revives every eliminated player |
| Bedrock | ✓ | — | Clears the bordered area down to bedrock |
| Chest | — | ✓ | Spawns loot supply drops |
| Obsidian | ✓ | ✓ | Removes player-placed blocks and crystals |
| Clock | ✓ | ✓ | Opens the 3 / 10 / 15 second countdown chooser |

The three plain toggles react to any click. Everything else **runs on left
click** and **flips its own on/off state on right click**, so a destructive
action is always one deliberate click away while still having a `%status%` you
can read and place in the lore.

### World border prompt

Clicking the barrier asks for a size in chat. The number is the **full width**,
not a radius:

```
1000 1s      a 1000-block border, reached over one second
500 15s      shrink to 500 over fifteen seconds
1000         1000 blocks, applied instantly
```

Durations accept `ms`, `s`, `m` and `h`; a bare number means seconds.

## Configuration

| File | Contents |
| --- | --- |
| `config.yml` | Chat prompt options and the per-gamemode toggle defaults |
| `messages.yml` | Every chat message |
| `settings.yml` | Menu layouts: slots, materials, names and lore |
| `modules/*.yml` | One file per module, each with its own `enabled` flag |
| `kits.yml` | Kits saved with `/kit create` (generated) |
| `data.yml` | Selections, toggles and spawn points (generated) |

Colour codes work everywhere: legacy `&a`, hex `&#AD4EFF` and repeated hex
`&x&F&F&B&A&0&0`.

Lore placeholders include `%status%`, `%mode%`, `%selected%`, `%alive%`,
`%dead%`, `%size%`, `%seconds%`, `%kit%`, `%tracked%` and `%progress%`. The
`ENABLED` / `DISABLED` text itself is the `status:` section of `settings.yml`,
so you choose its wording and colours.

## PlaceholderAPI

PlaceholderAPI is optional. When present, the `shardedcore` expansion registers:

```
%shardedcore_alive%            players still alive
%shardedcore_border%           border size, counting down 1000, 999, 998 ...
%shardedcore_dead%             eliminated players
%shardedcore_mode%             crystal, diasmp or none
%shardedcore_phase%            lobby, countdown, running or ended
%shardedcore_countdown%        seconds left
%shardedcore_setting_pvp%      any toggle, by id
%shardedcore_kit_diasmp%       selected kit for a gamemode
```

The full list is in `modules/placeholders.yml`.

## Modules

| Module | Feature |
| --- | --- |
| `announce` | `/announce` |
| `countdown` | `/countdown`, the countdown chooser and presets |
| `spawn` | `/setspawn`, `/spawn`, join and respawn placement, whitelist toggling |
| `kits` | Kit storage, `/kit`, auto-equip and auto-offhand |
| `protection` | PvP, locator bar, spawn protection, pre-start lockdown |
| `worldborder` | Border resizing and the border placeholder |
| `bedrockdrop` | Clears the bordered area down to bedrock |
| `clearblocks` | Removes player-placed blocks and crystals |
| `supplydrops` | Random loot chests |
| `death` | Spectator on death, glowing lootable head |
| `game` | `/start`, `/end`, revives, winner detection |
| `settings` | The `/settings` menus |
| `placeholders` | The PlaceholderAPI expansion |

A disabled module unregisters its listeners and cancels its tasks, so it costs
nothing at runtime.

## How the heavy operations stay smooth

Two features touch a very large number of blocks, and both are built so the
server keeps a full tick rate.

**Drop to bedrock** clears everything inside the border down to y −63. On a
200×200 border that is millions of blocks, so the work is split three ways:
chunks are fetched with `getChunkAtAsync` and snapshotted on the main thread;
the snapshots are then scanned **off** the main thread, skipping all-air
sections and everything above each column's heightmap, to produce a dense
`int[]` of positions; finally the main thread writes air with physics disabled
and stops as soon as it has spent its per-tick time budget. Writes go top-down
so skylight propagates once per column rather than once per block. Budgets live
in `modules/bedrockdrop.yml`; `instant: true` is available if you would rather
have the stall.

**Clear placed blocks** never scans the world. Placements are recorded as they
happen into a primitive open-addressed long set, so tracking a block is a couple
of array reads with no boxing, natural terrain is never touched, and removal is
budgeted per tick the same way.

Elsewhere: menus are shared between viewers rather than rebuilt per player,
click routing is a flat array indexed by slot, parsed colour components are
memoised, titles go out through one server-wide audience call instead of a
per-player loop, saved state is flushed asynchronously and coalesced, and the
chat-prompt and countdown systems run no idle tasks at all.
