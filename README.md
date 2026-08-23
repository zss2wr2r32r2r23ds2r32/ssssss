# ShardedVelocityCore

Velocity proxy plugin with server status placeholders, queue system, and hologram support.

## JARs (v1.0.8)

| File | Install on |
|------|------------|
| `ShardedVelocityCore-1.0.8.jar` | Velocity `plugins/` |
| `ShardedVelocityCore-Backend-1.0.8.jar` | Every backend server |
| `ShardedVelocityCore-Lobby-1.0.8.jar` | Lobby `plugins/` (+ PlaceholderAPI) |

## Lobby config (`plugins/ShardedVelocityCore-Lobby/config.yml`)

```yaml
motd: "&#8AFF00&lSHARDEDMC"

maintenance:
  kick-message:
    - "&#FF0000&lMAINTENANCE"
    - "&fThis server is currently in downtime"
  maintenance-motd: "&#FF0000&lMAINTENANCE"
  server-list:
    version-text: "Maintenance"
    protocol-version: -1
```

When `/maintenance` is on, the server list shows an **X** and "Maintenance". Syncs to Velocity ping too.

## Queue server colors

- survival: `&#8AFF00`
- events: `&#FFEE00`
- diasmp: `&#FF0000`

## Whitelist = Maintenance

Install Backend plugin on each server with matching `server-name`. Whitelist state is cached on Velocity and polled every second.
