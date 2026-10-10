# ShardedCore

Modular Paper 1.21 plugin for ShardedMC. Every feature lives in its own folder under `plugins/ShardedCore/modules/` with a `config.yml`. Disabled modules unregister their commands so they show **red** in-game.

## Build

```bash
mvn -q -DskipTests package
```

The jar is written to `target/ShardedCore-1.0.0.jar`.

## Modules

Announce, Commands (`/discord` `/store` `/apply`), Chat filter, Chat format, Settings, Coinflip, Combat, Crates, Death messages, Drop fix, Economy, Guide, Homes, Join messages, Kits, Live, Nametags, Ping, RTP, Sell, Spawn, TPA, Workstations.

Toggle modules with `/modules` or `/shardedcore modules <name> <on|off>`.
