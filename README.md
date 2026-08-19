# ShardedCore

All-in-one core plugin for **Paper 1.21.11** (Java 21). Every feature is a module with its own folder (`config.yml`, `messages.yml`, and optional `gui.yml` menus in DeluxeMenus-style format).

Pre-built jar: [`release/ShardedCore-1.4.11.jar`](release/ShardedCore-1.4.11.jar)

## Global config

`plugins/ShardedCore/config.yml` — global **prefix** and module toggles. Reload: `/shardedcore reload`

## GUI configs (DeluxeMenus-style)

Modules with `gui.yml` use the same format as DeluxeMenus / your leaderboard.yml:

```yaml
menu_title: '&5&lRTP ZONE'
size: 27
open_permission: sharded.rtp.use
items:
  teleport:
    material: ENDER_PEARL
    slot: 13
    display_name: '&5&lRANDOM TELEPORT'
    lore: ['&7Click to teleport']
    click_commands:
      - '[close]'
      - '[rtp_confirm]'
```

Supported commands: `[message]`, `[console]`, `[player]`, `[close]`, `[sound]`, `[openguimenu]`, `[tokens_take]`, plus module actions like `[rtp_confirm]`, `[toggle_chat]`.

## Modules (21)

| Module | Commands | Notes |
|---|---|---|
| craft | `/craft` | Portable crafting table |
| fix | `/fix` | 6h cooldown, `sharded.fix.bypass` |
| trash | `/trash` | Disposal GUI |
| chat | `/chattoggle` | Toggle public chat |
| privatemessages | `/msg`, `/reply`, `/msgtoggle` | PM system |
| nightvision | `/nv` | Persistent night vision |
| hide | `/hide` | Scrambled name (`&kaaaaaaaa`) + Steve skin |
| kill | `/kill [player]` | Kill command restored |
| deathmessages | — | Rank messages via LuckPerms permissions |
| joinmessages | — | Rank join/quit messages |
| backpack | `/backpack [player]` | **Single slot**, SQLite, view others offline |
| graves | `/graves` | Head + name + despawn timer hologram, `sharded.graves.use` for ranks |
| armortrims | `/armortrims` | 3-slot GUI, click to cycle trims |
| fly | `/fly` | **Requires region set** (`/fly pos1`, `/fly pos2`, `/fly setregion`) |
| autosmelt | `/autosmelt` | Pickaxe enchant |
| portalrtp | `/rtp` | Portal in `factions` opens `gui.yml`, RTP to `world` |
| settings | `/settings` | **Overrides other plugins**, DeluxeMenus-style GUI |
| killstreaks | — | Rewards at 5/10/15 with configurable commands |
| pickupmobs | — | Sneak+click mobs from config list |
| pickupspawners | `/spawners pay` | Pay tokens, silk touch mine spawners |
| tokens | `/bal`, `/tokens`, `/tokenshop` | Full token economy + shop |

## Tokens & placeholders

- `/bal` — check balance
- `/tokens give|set|remove|reset|giveall` — admin (offline player support)
- `/tokenshop` — full shop matching your tokenshop layout (menus in `modules/tokens/menus/`)
- PlaceholderAPI (optional): `%shardedcore_tokens%`, `%shardedcore_tokens_formatted%`

## Key permissions

```
sharded.graves.use          Give to ranks for graves on death
sharded.backpack.view.others  View other players' backpacks (offline OK)
sharded.fly.use             Fly inside set region (region MUST be set first)
sharded.pickupmobs.use      Pick up configured mobs
sharded.spawners.use        /spawners pay
sharded.spawners.pickup     Mine spawners after paying
sharded.tokens.admin        /tokens admin commands
sharded.tokenshop.use       Open token shop
```

## Build

```bash
mvn package
# -> target/ShardedCore-1.1.0.jar
```
