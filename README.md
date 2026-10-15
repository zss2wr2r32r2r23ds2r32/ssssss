# ShardedCore

Modular Paper **1.21.11** server core plugin. Each feature is an independent module that can be toggled in `config.yml` without removing commands from the server.

## Requirements

- Paper 1.21.11+
- Java 21
- Optional: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/), [LuckPerms](https://luckperms.net/)

## Build

```bash
mvn package -DskipTests
```

The shaded JAR is written to `target/ShardedCore-1.0.0-SNAPSHOT.jar`.

## Installation

1. Copy the JAR into your server's `plugins/` folder.
2. Start the server once to generate configs.
3. Edit `plugins/ShardedCore/config.yml` to enable or disable modules.
4. Reload with `/shardedcore reload`.

## Modules

| Module | Description |
|--------|-------------|
| `economy` | Balance, pay, baltop, admin economy commands |
| `live` | Live stream announcements |
| `links` | `/discord`, `/store`, `/apply` |
| `ping` | `/ping` |
| `trash` | `/trash` disposal GUI |
| `spawn` | `/spawn`, `/setspawn`, `/delspawn` |
| `homes` | `/home`, `/homes`, `/sethome`, `/delhome` |
| `tpa` | TPA request system |
| `rules` | `/rules` |
| `guide` | `/guide` |
| `announce` | `/announce` |
| `workstations` | Virtual crafting stations |
| `settings` | Player settings GUI and toggles |
| `coinflip` | `/cf` gambling |
| `combat` | Combat tagging |
| `commandwhitelist` | Restrict commands by permission group |
| `chatformat` | Chat formatting |
| `sell` | `/sell`, `/worth`, `/sellmulti` |
| `shop` | Multi-category shop GUI |
| `orders` | Player item orders |
| `kits` | Kit claims |
| `team` | Teams with GUI |
| `rtp` | Random teleport |
| `kill-rewards` | Kill milestone rewards |
| `playtime-rewards` | Playtime milestone rewards |
| `join-counter` | Unique join counter and join/quit messages |
| `media` | Media rank info |
| `crates` | Crate system |
| `death-messages` | Custom death messages |
| `dropfix` | Item drop fixes |
| `nametags` | Floating nametags |
| `staff` | Staff `/tp` |

Per-module configs live under `plugins/ShardedCore/modules/<id>/`.

## Admin commands

- `/shardedcore reload` — reload configs and modules
- `/shardedcore features` — list module status
- `/shardedcore placeholders` — list PlaceholderAPI identifiers
- `/shardedcore help`

## Placeholders

Requires PlaceholderAPI.

| Placeholder | Description |
|-------------|-------------|
| `%shardedcore_prefix%` | Plugin message prefix |
| `%shardedcore_balance%` | Raw balance |
| `%shardedcore_balance_formatted%` | Formatted balance |
| `%shardedcore_ping%` | Player ping |
| `%shardedcore_join_counter%` | Global join counter |
| `%shardedcore_module_<id>%` | `true`/`false` if module is enabled |

## Disabled modules

When a module is disabled in `config.yml`, its commands remain registered but have no executor, so Paper shows the vanilla unknown-command response.
