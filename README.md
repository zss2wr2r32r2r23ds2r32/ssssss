# ShardedVelocityCore

Velocity proxy plugin with server status placeholders, queue system, and hologram support.

## JARs

| File | Install on |
|------|------------|
| `build/libs/ShardedVelocityCore.jar` | Velocity `plugins/` |
| `lobby/build/libs/ShardedVelocityCore-Lobby.jar` | Lobby server `plugins/` (for `%` hologram placeholders) |

Build: `./gradlew build`

## Install

1. Put **ShardedVelocityCore.jar** on Velocity
2. Put **ShardedVelocityCore-Lobby.jar** on your lobby (requires PlaceholderAPI)
3. Restart Velocity and lobby
4. Make sure server names in `config.toml` match your `velocity.toml` registrations

## /queue

- `/queue` — join default server queue (no permission required)
- `/queue survival|events|diamondsmp`
- `/queue leave`

## Hologram placeholders (line 9)

**DecentHolograms / PlaceholderAPI (lobby):**
```
%shardedvelocitycore_status_survival%
%shardedvelocitycore_status_events%
%shardedvelocitycore_status_diamondsmp%
```

**MiniPlaceholders format (proxy + lobby):**
```
<shardedvelocitycore_status_survival>
<shardedvelocitycore_status_events>
<shardedvelocitycore_status_diamondsmp>
```

Status values:
- Online: `&#8AFF00&lONLINE`
- Offline: `&#FF0000&lOFFLINE`
- Maintenance: `&#FF0000&lMAINTEANCE`

## Configuration

`plugins/shardedvelocitycore/config.toml`

```toml
tracked-servers = ["survival", "events", "diamondsmp"]
maintenance-servers = []
```

## Requirements

- Velocity 3.3+
- Java 21+
- PlaceholderAPI on lobby (for `%` holograms)
- MiniPlaceholders on Velocity + lobby (optional, for `<>` format)
