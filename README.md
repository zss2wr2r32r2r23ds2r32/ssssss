# ShardedVelocityCore

Velocity proxy plugin with server status placeholders, queue system, and hologram support.

## JARs (v1.0.7)

| File | Install on |
|------|------------|
| `ShardedVelocityCore-1.0.7.jar` | Velocity `plugins/` |
| `ShardedVelocityCore-Backend-1.0.7.jar` | Every backend server (survival, events, diasmp) |
| `ShardedVelocityCore-Lobby-1.0.7.jar` | Lobby `plugins/` (+ PlaceholderAPI) |

## Lobby maintenance

| Command | Description |
|---------|-------------|
| `/maintenance` | Toggle maintenance — kicks non-bypass players with maintenance screen |
| `/maintenance add <player>` | Add player to bypass list (tab complete) |
| `/maintenance remove <player>` | Remove player from bypass list |
| `/maintenance wipe` | Clear entire bypass list |

Permission: `shardedvelocitycore.maintenance` (default: op)

## Whitelist = Maintenance

When whitelist is **on** on a backend server, hologram status shows **MAINTENANCE**.

Install `ShardedVelocityCore-Backend` on each backend and set `server-name` in config.

## Queue server colors

- survival: `&#8AFF00`
- events: `&#FF0700`
- diasmp: `&#FF0000`
