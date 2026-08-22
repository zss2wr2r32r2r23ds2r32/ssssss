# ShardedVelocityCore

A **Velocity proxy plugin** with server status placeholders, queue system, and hologram support for Survival, Events, and DiamondSMP.

## Download

Build output: `build/libs/ShardedVelocityCore.jar`

```bash
./gradlew build
```

Install by placing `ShardedVelocityCore.jar` in your Velocity `plugins/` folder.

## Status Placeholders

Automatically returns the correct coloured status for each server:

| State | Output |
|-------|--------|
| Online | `&#8AFF00&lONLINE` |
| Offline | `&#FF0000&lOFFLINE` |
| Maintenance | `&#FF0000&lMAINTEANCE` |

Use on hologram **line 9** (keep icon colours on other lines unchanged):

| Placeholder | Server |
|-------------|--------|
| `%shardedvelocitycore_status_survival%` | Survival |
| `%shardedvelocitycore_status_events%` | Events |
| `%shardedvelocitycore_status_diamondsmp%` | DiamondSMP |

> Requires [MiniPlaceholders](https://hangar.papermc.io/MiniPlaceholders/MiniPlaceholders) on Velocity and backend hologram servers.

## Queue

- `/queue` — join default server queue (no permission required)
- `/queue survival|events|diamondsmp` — join a specific server
- `/queue leave` — leave the queue

**Action bar:** `#%numberinqueue% in queue to &n&#8AFF00%server%&r &7(Wating: %numberofpeoplewaitinginqueue%)`

**Prefix:** `&#4498DB&lQUEUE &8▷&r`

## Configuration

`plugins/shardedvelocitycore/config.toml`

```toml
maintenance-servers = ["survival"]  # force MAINTEANCE status
```

## Requirements

- Velocity 3.3+
- Java 21+
