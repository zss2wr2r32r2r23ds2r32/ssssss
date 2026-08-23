# ShardedVelocityCore

Velocity proxy plugin with server status placeholders, queue system, and hologram support.

## JARs (v1.0.5)

| File | Install on |
|------|------------|
| `ShardedVelocityCore-1.0.5.jar` | Velocity `plugins/` |
| `ShardedVelocityCore-Lobby-1.0.5.jar` | Lobby `plugins/` (+ PlaceholderAPI) |

## Commands

| Command | Description |
|---------|-------------|
| `/queue [server]` | Join queue — includes `lobby` |
| `/queue lobby` | Return to lobby |
| `/server lobby` | Connect to lobby |
| `/server <server>` | Connect via queue system |
| `/leave` | Leave the queue |

## Whitelist = Maintenance

When a server has whitelist enabled, its hologram status automatically shows **MAINTEANCE**.

Detects whitelist via server MOTD and kick messages.

```toml
whitelist-as-maintenance = true
```

## Config

```toml
lobby-server = "lobby"
queue-servers = ["lobby", "survival", "events", "diasmp"]
tracked-servers = ["survival", "events", "diasmp"]
```

Make sure your Velocity `velocity.toml` has a server named `lobby`.
