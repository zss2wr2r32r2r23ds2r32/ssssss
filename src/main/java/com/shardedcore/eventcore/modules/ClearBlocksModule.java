package com.shardedcore.eventcore.modules;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.module.EventModule;
import com.shardedcore.eventcore.util.LongHashSet;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Removes blocks and entities that players placed during the event.
 *
 * <p>Placements are recorded as they happen rather than discovered by scanning
 * the world, so a clear touches exactly the positions players created — no
 * terrain is ever harmed. Positions live in a primitive
 * {@link LongHashSet}, which keeps a few hundred thousand tracked obsidian
 * blocks at eight bytes each with no boxing on the event hot path.</p>
 *
 * <p>The removal itself is budgeted per tick and writes with physics disabled,
 * so clearing a crystal-PvP arena does not produce a TPS dip.</p>
 */
public final class ClearBlocksModule extends EventModule {

    private final BlockData air = Material.AIR.createBlockData();

    private Set<Material> trackedMaterials = EnumSet.noneOf(Material.class);
    private Set<EntityType> trackedEntities = EnumSet.noneOf(EntityType.class);
    private int maxTracked = 500_000;

    private final Map<UUID, LongHashSet> placedByWorld = new HashMap<>();
    private final Set<UUID> placedEntities = new HashSet<>();

    private BukkitTask clearTask;

    public ClearBlocksModule(ShardedEventCore plugin) {
        super(plugin, "clearblocks", "Removes player-placed blocks and entities such as obsidian and crystals.");
    }

    @Override
    protected void onModuleEnable() {
        readTargets();
    }

    @Override
    protected void onConfigReload() {
        readTargets();
    }

    @Override
    protected void onModuleDisable() {
        cancelClear();
        placedByWorld.clear();
        placedEntities.clear();
    }

    private void readTargets() {
        FileConfiguration config = config().raw();
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String raw : config.getStringList("materials")) {
            Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
            if (material != null && material.isBlock()) {
                materials.add(material);
            } else {
                plugin.getLogger().warning("clearblocks: '" + raw + "' is not a block material.");
            }
        }
        trackedMaterials = materials;

        Set<EntityType> entities = EnumSet.noneOf(EntityType.class);
        for (String raw : config.getStringList("entity-types")) {
            try {
                entities.add(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("clearblocks: '" + raw + "' is not an entity type.");
            }
        }
        trackedEntities = entities;
        maxTracked = Math.max(1024, config.getInt("max-tracked-blocks", 500_000));
    }

    public int trackedCount() {
        int total = 0;
        for (LongHashSet set : placedByWorld.values()) {
            total += set.size();
        }
        return total;
    }

    public boolean isClearing() {
        return clearTask != null;
    }

    /** Forgets every recorded placement without removing anything. */
    public void resetTracking() {
        placedByWorld.clear();
        placedEntities.clear();
    }

    // ----------------------------------------------------------------- tracking

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (!trackedMaterials.contains(type)) {
            return;
        }
        World world = event.getBlock().getWorld();
        LongHashSet set = placedByWorld.computeIfAbsent(world.getUID(), unused -> new LongHashSet(4096));
        if (set.size() >= maxTracked) {
            return;
        }
        set.add(LongHashSet.pack(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        untrack(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            untrack(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            untrack(block);
        }
    }

    private void untrack(Block block) {
        if (!trackedMaterials.contains(block.getType())) {
            return;
        }
        LongHashSet set = placedByWorld.get(block.getWorld().getUID());
        if (set != null) {
            set.remove(LongHashSet.pack(block.getX(), block.getY(), block.getZ()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (trackedEntities.contains(event.getEntity().getType())) {
            placedEntities.add(event.getEntity().getUniqueId());
        }
    }

    // ------------------------------------------------------------------ clear

    /**
     * Removes everything tracked, spread over as many ticks as the budget needs.
     *
     * @param onComplete run on the main thread with blocks and entities removed
     * @return false when a clear is already in progress
     */
    public boolean clear(Consumer<int[]> onComplete) {
        if (clearTask != null) {
            return false;
        }
        FileConfiguration config = config().raw();
        boolean physics = config.getBoolean("apply-physics", false);
        long budgetNanos = Math.max(1L, config.getLong("max-millis-per-tick", 6L)) * 1_000_000L;
        int perTick = Math.max(256, config.getInt("max-blocks-per-tick", 20_000));

        int entitiesRemoved = clearEntities();

        List<WorldBatch> batches = new ArrayList<>(placedByWorld.size());
        for (Map.Entry<UUID, LongHashSet> entry : placedByWorld.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world != null && !entry.getValue().isEmpty()) {
                batches.add(new WorldBatch(world, entry.getValue().toArray()));
            }
        }
        placedByWorld.clear();

        if (batches.isEmpty()) {
            if (onComplete != null) {
                onComplete.accept(new int[]{0, entitiesRemoved});
            }
            return true;
        }

        int[] counters = {0, entitiesRemoved};
        int[] cursor = {0, 0};

        clearTask = track(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long deadline = System.nanoTime() + budgetNanos;
            int processed = 0;

            while (cursor[0] < batches.size()) {
                WorldBatch batch = batches.get(cursor[0]);
                long[] positions = batch.positions();
                while (cursor[1] < positions.length) {
                    long packed = positions[cursor[1]++];
                    Block block = batch.world().getBlockAt(
                            LongHashSet.unpackX(packed),
                            LongHashSet.unpackY(packed),
                            LongHashSet.unpackZ(packed));
                    if (trackedMaterials.contains(block.getType())) {
                        block.setBlockData(air, physics);
                        counters[0]++;
                    }
                    if (++processed >= perTick || System.nanoTime() >= deadline) {
                        return;
                    }
                }
                cursor[0]++;
                cursor[1] = 0;
            }

            cancelClear();
            if (onComplete != null) {
                onComplete.accept(counters);
            }
        }, 1L, 1L));
        return true;
    }

    private record WorldBatch(World world, long[] positions) {
    }

    /**
     * Removes the tracked entity types. When {@code only-player-placed} is off it
     * sweeps every matching entity in the world instead of only recorded ones.
     */
    private int clearEntities() {
        if (trackedEntities.isEmpty()) {
            return 0;
        }
        boolean onlyPlaced = config().raw().getBoolean("only-player-placed", true);
        int removed = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!trackedEntities.contains(entity.getType())) {
                    continue;
                }
                if (onlyPlaced && !placedEntities.contains(entity.getUniqueId())) {
                    continue;
                }
                entity.remove();
                removed++;
            }
        }
        placedEntities.clear();
        return removed;
    }

    private void cancelClear() {
        if (clearTask != null) {
            clearTask.cancel();
            clearTask = null;
        }
    }
}
