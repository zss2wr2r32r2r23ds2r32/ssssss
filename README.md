# ShardedVelocityCore

Velocity proxy plugin with server status placeholders, queue system, and hologram support.

## JARs (v1.0.2)

| File | Install on |
|------|------------|
| `build/libs/ShardedVelocityCore.jar` | Velocity `plugins/` |
| `lobby/build/libs/ShardedVelocityCore-Lobby.jar` | Lobby `plugins/` (+ PlaceholderAPI) |

Build: `./gradlew build`

## Commands

| Command | Description |
|---------|-------------|
| `/queue [server]` | Join queue (defaults to survival) |
| `/queue leave` | Leave the queue |
| `/server <server>` | Connect via queue system |

Both `/queue` and `/server` use the same queue logic — if the server is full or offline, you are queued automatically.

## Hologram placeholders (line 9)

```
%shardedvelocitycore_status_survival%
%shardedvelocitycore_status_events%
%shardedvelocitycore_status_diamondsmp%
```

Status updates every **1 second** live — no need to leave and rejoin.

## Queue colors

Configure in `plugins/shardedvelocitycore/config.toml`:

```toml
[queue.colors]
  position = "&#FFFFFF"
  server = "&#8AFF00"
  waiting = "&#AAAAAA"
  success = "&#8AFF00"
  error = "&#FF0000"
  accent = "&#4498DB"

[queue.server-colors]
  survival = "&#8AFF00"
  events = "&#FFAA00"
  diamondsmp = "&#4498DB"
```

Action bar supports: `%numberinqueue%`, `%server%`, `%server_color%`, `%numberofpeoplewaitinginqueue%`, `%accent_color%`, `%position_color%`, `%waiting_color%`

## Faster status updates

```toml
status-refresh-seconds = 1
status-sync-interval-seconds = 1
```

Status changes broadcast to lobby immediately and holograms refresh automatically.
