# ShardedSMP

Paper plugin for a border event SMP: banned vanilla obsidian, sky-drop shards, grace, phases, a community diamond quest, and boss rewards.

## Requirements

- Paper 1.21.4+
- Java 21

## Build

```bash
mvn -B package
```

The jar is written to `target/ShardedSMP.jar`. Drop it in your server `plugins` folder.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/grace start` | `shardedsmp.admin` | Starts the 30-minute grace period, 1500x1500 border, RTP, and 32 steak |
| `/obsidian test` | `shardedsmp.admin` | Spawns a glowing test obsidian shard from the sky |
| `/obsidian status` | `shardedsmp.admin` | Shows event progress |
| `/diamonds` | everyone | Shows community diamond quest progress |

## Event flow

1. **`/grace start`** — Phase 1 title, 30-minute action-bar countdown, no mob spawns, no PvP, every player is randomly teleported inside the 1500x1500 border and given 32 steak.
2. After grace ends, **obsidian does not spawn immediately**. It waits a random delay and needs **5 online players**. Pieces fall from the sky **one at a time** at random intervals until **10** have spawned.
3. **5 shards found** → Phase 2.
4. **10 shards found** → Nether opens (portal spawned in the overworld, coords on the action bar), Phase 3, and an event Wither waits in a nether bedrock arena.
5. **500 diamond ore mined** (community, natural ore only) → Phase 4 and a filled End portal is spawned at random coords.
6. **Ender dragon killed** → Phase 5. Top damage dealer receives the dragon egg.

## Rules baked in

- Water/lava cannot create obsidian (it becomes cobblestone).
- Vanilla obsidian cannot be mined, picked up, or placed. Only sky-drop shards exist.
- Event obsidian glows yellow, never despawns, cannot be dropped, and cannot be put into chests/GUIs/hoppers/item frames.
- Players holding a shard also glow yellow. After all 10 spawn, the action bar cycles holder names.
- Enchant caps: Phase 1 prot 2 / sharp 1, Phase 2 prot 3 / sharp 4, Phase 3+ prot 4 / sharp 5. Other enchants are uncapped. Phase 3+ netherite tools only.
- Kill streaks: 3 fire resistance, 5 speed II, 10 strength II. Dying removes the effects.
- Dragon egg in inventory = 2 extra hearts.
- Event Wither drops 1 enchanted golden apple, 16 wind charges, flow armor trim, 1 totem.
- Ender dragon extra drops: 3 enchanted golden apples, 64 wind charges, silence ("stealth") armor trim, 4 totems.
