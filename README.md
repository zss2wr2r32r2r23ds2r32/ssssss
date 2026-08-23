# ShardedVelocityCore

Velocity proxy plugin with server status placeholders, queue system, and hologram support.

## JARs (v1.0.6)

| File | Install on |
|------|------------|
| `ShardedVelocityCore-1.0.6.jar` | Velocity `plugins/` |
| `ShardedVelocityCore-Backend-1.0.6.jar` | **Every backend server** (survival, events, diasmp, etc.) |
| `ShardedVelocityCore-Lobby-1.0.6.jar` | Lobby `plugins/` (+ PlaceholderAPI) |

## Whitelist = Maintenance

When whitelist is **on** on a backend server (e.g. survival), hologram status shows **MAINTEANCE**.

Install `ShardedVelocityCore-Backend` on each backend server and set `server-name` in its config to match `velocity.toml`:

```yaml
# plugins/ShardedVelocityCore-Backend/config.yml
server-name: survival
```

The backend plugin reports `Bukkit.hasWhitelist()` to Velocity every second. When whitelist is toggled, status updates instantly.

```toml
whitelist-as-maintenance = true
```

## Commands

| Command | Description |
|---------|-------------|
| `/queue [server]` | Join queue — includes `lobby` |
| `/queue lobby` | Return to lobby |
| `/server lobby` | Connect to lobby |
| `/server <server>` | Connect via queue system |
| `/leave` | Leave the queue |

## Config

```toml
lobby-server = "lobby"
queue-servers = ["lobby", "survival", "events", "diasmp"]
tracked-servers = ["survival", "events", "diasmp"]
whitelist-as-maintenance = true
```

Make sure your Velocity `velocity.toml` has a server named `lobby`.
