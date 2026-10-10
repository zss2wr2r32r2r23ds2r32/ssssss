# ShardedVelocityCore

## JARs (v1.0.9)

| File | Install on |
|------|------------|
| `ShardedVelocityCore-1.0.9.jar` | Velocity `plugins/` |
| `ShardedVelocityCore-Backend-1.0.9.jar` | Backend servers |
| `ShardedVelocityCore-Lobby-1.0.9.jar` | Lobby (+ PlaceholderAPI) |

## Lobby MOTD config

Edit `plugins/ShardedVelocityCore-Lobby/config.yml` (mdMOTD-style):

```yaml
motd:
  lines:
    - "§x§a§d§4§e§f§f§lSHARDEDMC ..."
    - "§x§8§A§F§F§0§0§lWELCOME"

server-icon:
  enabled: true
  image: "default-server-icon.png"

maintenance-motd:
  kick-message:
    - "&#FF0000&lMAINTENANCE"
    - "&fThis server is currently in downtime"
  text: "&cMAINTENANCE"
  protocol-version: -1
  motds:
    - line1: "..."
      line2: "§x§F§F§0§0§0§0§lMAINTENANCE"
      icon: "maintenance.png"
```

**Icons:** Put 64x64 PNG files in `plugins/ShardedVelocityCore-Lobby/icons/`  
Also copy the same icons to `plugins/ShardedVelocityCore/icons/` on Velocity for proxy ping.

Kick screen no longer shows "You were kicked from lobby" — uses a direct disconnect.
