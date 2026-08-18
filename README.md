# ShardedCore

All-in-one core plugin for **Paper 1.21.11** (Java 21). Every feature is a module: each module has its own folder with a `config.yml` and `messages.yml`, and every module can be turned on/off in the main `config.yml`.

A pre-built jar is in [`release/ShardedCore-1.0.0.jar`](release/ShardedCore-1.0.0.jar).

## Modules

| Module | Command(s) | What it does |
|---|---|---|
| craft | `/craft` | Portable crafting table |
| fix | `/fix` | Repairs held item, 6h cooldown (`sharded.fix.bypass` to skip) |
| trash | `/trash` | Disposal GUI, items deleted on close |
| chat | `/chattoggle` | Toggle seeing public chat |
| privatemessages | `/msg`, `/reply`, `/msgtoggle` | Private messages + toggle receiving them |
| nightvision | `/nightvision` (`/nv`) | Infinite night vision toggle, persists relogs |
| hide | `/hide` | Swaps your nickname + skin to Steve |
| deathmessages | — | Rank-based death messages (LuckPerms permissions, `%player%`, `%rank%`, `%killer%`) |
| joinmessages | — | Rank-based join/quit/first-join messages |
| backpack | `/backpack` (`/bp`) | Extra storage in a SQLite DB; `sharded.backpack.level.N` = +N rows |
| graves | `/graves` (admin) | On death: player head + hologram (name, timer, XP); right-click to loot |
| armortrims | `/armortrims` | Trim station GUI: armor in middle, patterns left, materials right, confirm (no netherite) |
| fly | `/fly` | Region-restricted flight in the `factions` world; `/fly speed <1-10>`, `/fly <player>`, `/fly pos1/pos2/setregion` |
| autosmelt | `/autosmelt` | Auto Smelt enchant for pickaxes - mined ores drop smelted |
| portalrtp | `/rtp` | Touch a nether portal in `factions` → GUI → random teleport into `world` |
| settings | `/settings` | Personal settings GUI (chat, PMs, night vision) |

## Key permissions

```
sharded.admin                 /shardedcore reload
sharded.fix.bypass            skip the /fix cooldown
sharded.backpack.level.1-5    +1 to +5 backpack rows (give via LuckPerms)
sharded.graves.use            create a grave on death
sharded.graves.bypass         open anyone's grave
sharded.deathmessages.<rank>  rank death message formats (configurable)
sharded.joinmessages.<rank>   rank join message formats (configurable)
sharded.fly.use / speed / others / anywhere / admin
sharded.autosmelt.use         apply the Auto Smelt enchant
sharded.rtp.use / bypass      portal RTP + cooldown/world bypass
```

The full list is in [`src/main/resources/plugin.yml`](src/main/resources/plugin.yml).

## Setup notes

- **Fly region:** stand at one corner → `/fly pos1`, other corner → `/fly pos2`, then `/fly setregion`. Until a region is set, `/fly` refuses to work. Creative/spectator players are never force-cancelled.
- **Ranks:** give format permissions through LuckPerms, e.g. `/lp group vip permission set sharded.deathmessages.vip true`. `%rank%` resolves to the LuckPerms prefix.
- **Portal RTP worlds:** change `portal-world` / `target-world` in `plugins/ShardedCore/modules/portalrtp/config.yml`.
- **Reload everything:** `/shardedcore reload`.

## Building

```bash
mvn package
# -> target/ShardedCore-1.0.0.jar
```

Requires Java 21 and Maven. LuckPerms is a soft dependency (optional at runtime).
