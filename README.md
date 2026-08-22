# ShardedVelocityCore

Custom Velocity proxy plugin with server status placeholders, queue system, and hologram sync for Survival, Events, and DiamondSMP.

## Built JARs

| File | Install on |
|------|------------|
| `velocity/build/libs/ShardedVelocityCore-1.0.0.jar` | Velocity `plugins/` |
| `placeholderapi/build/libs/ShardedVelocityCore-PlaceholderAPI-1.0.0.jar` | Paper/Spigot backend servers with holograms |

Build:

```bash
./gradlew build
```

## Status Placeholders

Use on hologram line 9 (DecentHolograms, HolographicDisplays, etc.) via PlaceholderAPI:

| Placeholder | Output |
|-------------|--------|
| `%shardedvelocitycore_status_survival%` | `&#8AFF00&lONLINE` / `&#FF0000&lOFFLINE` / `&#FF0000&lMAINTEANCE` |
| `%shardedvelocitycore_status_events%` | Same format |
| `%shardedvelocitycore_status_diamondsmp%` | Same format |

Status updates automatically every 5 seconds (configurable).

### Hologram example (line 9)

Keep your existing server icon/colour on other lines; only replace line 9 with the placeholder:

```
&r
&#4498DB&lSURVIVAL
&7Click to join
...
%shardedvelocitycore_status_survival%
```

## Queue

- `/queue` — join the default server queue (no permission required)
- `/queue survival` — join a specific server queue
- `/queue events`
- `/queue diamondsmp`
- `/queue leave` — leave the queue

**Action bar:** `#%numberinqueue% in queue to &n&#8AFF00%server%&r &7(Wating: %numberofpeoplewaitinginqueue%)`

**Prefix:** `&#4498DB&lQUEUE &8▷&r`

## Configuration

Edit `plugins/shardedvelocitycore/config.toml` on Velocity after first run.

```toml
maintenance-servers = ["survival"]  # force MAINTEANCE status
```

## Requirements

- Velocity 3.3+
- PlaceholderAPI (backend hologram servers)
- Java 21+
