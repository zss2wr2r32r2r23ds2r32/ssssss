# ShardedLobbyCore

A modular, optimized lobby core plugin for **ShardedMC** — built for Paper/Spigot 1.20+.

## Features

ShardedLobbyCore is fully modular. Every feature lives in its own module under `plugins/ShardedLobbyCore/modules/` and can be disabled individually.

| Module | Description |
|--------|-------------|
| **Default Items** | Hotbar items on join (server selector in slot 4) |
| **Player Visibility** | Toggle hiding/showing all players |
| **Parkour** | Right-click emerald block to run `/ajparkour start` |
| **PvP Arena** | Hold sword 5s to enter/leave PvP with full kit |
| **Bow Popper** | Teleport wherever your arrow lands (5s cooldown) |
| **Double Jump** | Mid-air jump launches you forward |
| **Launch Pads** | Pressure plates trigger launch effect |
| **Join Messages** | First join title, welcome chat, adventure mode |
| **Announcements** | Rotating server announcements |
| **Chat Prefixes** | Permission-based chat prefixes |
| **Void Spawn** | Teleport to spawn when falling in void |
| **Spawn** | `/setspawn` command |
| **Join Actions** | Fireworks + teleport to spawn on join |
| **Command Whitelist** | Restrict commands in lobby |
| **Anti Swear** | Block inappropriate language |
| **Moderation** | `/clearchat` and `/lockchat` |
| **World Protection** | Disable damage, hunger, block break, etc. |

## Installation

1. Build with Maven: `mvn clean package`
2. Copy `target/ShardedLobbyCore-1.0.0.jar` to your server's `plugins/` folder
3. Restart the server
4. Run `/setspawn` to set the lobby spawn
5. Edit configs in `plugins/ShardedLobbyCore/`

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/setspawn` | `shardedlobbycore.setspawn` | Set lobby spawn |
| `/clearchat` | `shardedlobbycore.clearchat` | Clear chat |
| `/lockchat` | `shardedlobbycore.lockchat` | Toggle chat lock |
| `/shardedlobbycore reload` | `shardedlobbycore.admin` | Reload all configs |

## Configuration Structure

```
plugins/ShardedLobbyCore/
├── config.yml
├── messages.yml
└── modules/
    ├── default-items.yml
    ├── player-visibility.yml
    ├── parkour.yml
    ├── pvp.yml
    ├── bow-popper.yml
    ├── double-jump.yml
    ├── launch-pads.yml
    ├── join-messages.yml
    ├── announcements.yml
    ├── chat-prefixes.yml
    ├── void-spawn.yml
    ├── spawn.yml
    ├── join-actions.yml
    ├── command-whitelist.yml
    ├── anti-swear.yml
    ├── moderation.yml
    └── world-protection.yml
```

## Disabling Modules

Set `enabled: false` in any module's YAML file, then run `/shardedlobbycore reload`.

## PlaceholderAPI

Supports `%player%` natively. With PlaceholderAPI installed, all placeholders are supported in messages and item lore.

## Requirements

- Paper or Spigot 1.20+
- Java 17+
