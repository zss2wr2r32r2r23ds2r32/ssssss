# ShardedVelocityCore

Velocity proxy plugin with server status placeholders, queue system, and hologram support.

## JARs (v1.0.4)

| File | Install on |
|------|------------|
| `build/libs/ShardedVelocityCore-1.0.4.jar` | Velocity `plugins/` |
| `lobby/build/libs/ShardedVelocityCore-Lobby-1.0.4.jar` | Lobby `plugins/` (+ PlaceholderAPI) |

Build: `./gradlew build`

## Commands

| Command | Description |
|---------|-------------|
| `/queue [server]` | Join queue (defaults to survival) |
| `/queue leave` | Leave the queue |
| `/leave` | Leave the queue |
| `/server <server>` | Connect via queue system |

## Hologram placeholders (line 9)

```
%shardedvelocitycore_status_survival%
%shardedvelocitycore_status_events%
%shardedvelocitycore_status_diasmp%
```
