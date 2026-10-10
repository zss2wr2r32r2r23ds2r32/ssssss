package com.shardedcore.eventcore.modules;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.module.EventModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Scatters loot containers around the arena for the DiamondSMP mode.
 *
 * <p>Placements go through {@code getChunkAtAsync}, so dropping a batch of
 * chests into unexplored parts of the border never blocks the main thread on
 * chunk generation. Loot tables are read from configuration, and only the
 * entries listed there can ever appear in a drop.</p>
 */
public final class SupplyDropModule extends EventModule {

    /** One configured loot entry. */
    private record LootEntry(Material material, int min, int max, double chance, int weight) {

        ItemStack roll(ThreadLocalRandom random) {
            int amount = min >= max ? Math.max(1, min) : random.nextInt(min, max + 1);
            return new ItemStack(material, Math.max(1, amount));
        }
    }

    private List<LootEntry> loot = List.of();
    private int totalWeight;
    private BukkitTask autoTask;

    public SupplyDropModule(ShardedEventCore plugin) {
        super(plugin, "supplydrops", "Random loot chests for the DiamondSMP mode.");
    }

    @Override
    protected boolean hasListeners() {
        return false;
    }

    @Override
    protected void onModuleEnable() {
        readLoot();
        startAutoTask();
    }

    @Override
    protected void onConfigReload() {
        readLoot();
        stopAutoTask();
        startAutoTask();
    }

    @Override
    protected void onModuleDisable() {
        stopAutoTask();
    }

    private void readLoot() {
        List<LootEntry> entries = new ArrayList<>();
        int weight = 0;
        List<?> raw = config().raw().getList("loot.items");
        if (raw != null) {
            for (Object element : raw) {
                if (!(element instanceof java.util.Map<?, ?> map)) {
                    continue;
                }
                Material material = Material.matchMaterial(
                        String.valueOf(map.get("material")).trim().toUpperCase(Locale.ROOT));
                if (material == null || material.isAir()) {
                    plugin.getLogger().warning("supplydrops: unknown material " + map.get("material"));
                    continue;
                }
                int min = readInt(map.get("min"), 1);
                int max = readInt(map.get("max"), Math.max(min, 1));
                double chance = readDouble(map.get("chance"), 1.0D);
                int entryWeight = Math.max(1, readInt(map.get("weight"), 1));
                entries.add(new LootEntry(material, Math.max(1, min), Math.max(min, max), chance, entryWeight));
                weight += entryWeight;
            }
        }
        loot = List.copyOf(entries);
        totalWeight = weight;
    }

    private static int readInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double readDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private void startAutoTask() {
        if (!config().raw().getBoolean("auto.enabled", false)) {
            return;
        }
        long interval = Math.max(1L, config().raw().getLong("auto.interval-seconds", 120L)) * 20L;
        autoTask = track(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.state().running()) {
                spawn(config().raw().getInt("auto.count", config().raw().getInt("count", 5)), null);
            }
        }, interval, interval));
    }

    private void stopAutoTask() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
    }

    public int defaultCount() {
        return Math.max(1, config().raw().getInt("count", 5));
    }

    /**
     * Spawns {@code count} loot containers inside the configured area.
     *
     * @param onComplete run on the main thread with the number actually placed
     */
    public boolean spawn(int count, Consumer<Integer> onComplete) {
        World world = resolveWorld();
        if (world == null || loot.isEmpty()) {
            return false;
        }
        FileConfiguration config = config().raw();
        double[] area = resolveArea(world, config);
        double centreX = area[0];
        double centreZ = area[1];
        double half = area[2];

        Material containerType = Material.matchMaterial(
                config.getString("container", "CHEST").toUpperCase(Locale.ROOT));
        if (containerType == null || !containerType.isBlock()) {
            containerType = Material.CHEST;
        }

        int minY = config.getInt("min-y", Integer.MIN_VALUE);
        int requested = Math.max(1, count);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int[] placed = {0};
        int[] remaining = {requested};
        Material finalContainer = containerType;
        List<Location> used = new ArrayList<>(requested);
        double spacing = Math.max(0.0D, config.getDouble("min-distance-between", 16.0D));

        for (int attempt = 0; attempt < requested; attempt++) {
            int x = (int) Math.round(centreX + random.nextDouble(-half, half));
            int z = (int) Math.round(centreZ + random.nextDouble(-half, half));

            world.getChunkAtAsync(x >> 4, z >> 4, true).thenAccept(chunk -> {
                try {
                    if (chunk == null) {
                        return;
                    }
                    Location target = surface(world, x, z, minY);
                    if (target == null || tooClose(used, target, spacing)) {
                        return;
                    }
                    Block block = target.getBlock();
                    block.setType(finalContainer, false);
                    if (block.getState() instanceof Container container) {
                        fill(container.getInventory(), random);
                        container.update(true, false);
                    }
                    used.add(target);
                    placed[0]++;
                    announce(target);
                } finally {
                    if (--remaining[0] <= 0 && onComplete != null) {
                        onComplete.accept(placed[0]);
                    }
                }
            }).exceptionally(throwable -> {
                // A failed chunk load must still count towards the batch,
                // otherwise the completion callback would never fire.
                if (--remaining[0] <= 0 && onComplete != null) {
                    onComplete.accept(placed[0]);
                }
                return null;
            });
        }
        return true;
    }

    private boolean tooClose(List<Location> used, Location candidate, double spacing) {
        if (spacing <= 0.0D) {
            return false;
        }
        double squared = spacing * spacing;
        for (Location existing : used) {
            if (existing.getWorld() == candidate.getWorld()
                    && existing.distanceSquared(candidate) < squared) {
                return true;
            }
        }
        return false;
    }

    /** Finds the block just above the surface, respecting an optional floor. */
    private Location surface(World world, int x, int z, int minY) {
        int y = world.getHighestBlockYAt(x, z) + 1;
        if (minY != Integer.MIN_VALUE && y < minY) {
            y = minY;
        }
        if (y <= world.getMinHeight() || y >= world.getMaxHeight()) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    private void announce(Location location) {
        if (!config().raw().getBoolean("announce", true)) {
            return;
        }
        plugin.messages().broadcast("supplydrops.spawned",
                "%x%", Integer.toString(location.getBlockX()),
                "%y%", Integer.toString(location.getBlockY()),
                "%z%", Integer.toString(location.getBlockZ()));
    }

    /** Rolls a random subset of the configured loot into the container. */
    private void fill(Inventory inventory, ThreadLocalRandom random) {
        inventory.clear();
        int size = inventory.getSize();
        ConfigurationSection lootSection = config().raw().getConfigurationSection("loot");
        int minStacks = lootSection == null ? 3 : lootSection.getInt("min-stacks", 3);
        int maxStacks = lootSection == null ? 6 : lootSection.getInt("max-stacks", 6);
        int stacks = minStacks >= maxStacks ? Math.max(1, minStacks) : random.nextInt(minStacks, maxStacks + 1);

        for (int index = 0; index < stacks; index++) {
            LootEntry entry = pick(random);
            if (entry == null || random.nextDouble() > entry.chance()) {
                continue;
            }
            int slot = random.nextInt(size);
            for (int probe = 0; probe < size; probe++) {
                int candidate = (slot + probe) % size;
                if (inventory.getItem(candidate) == null) {
                    inventory.setItem(candidate, entry.roll(random));
                    break;
                }
            }
        }
    }

    private LootEntry pick(ThreadLocalRandom random) {
        if (loot.isEmpty()) {
            return null;
        }
        if (totalWeight <= loot.size()) {
            return loot.get(random.nextInt(loot.size()));
        }
        int roll = random.nextInt(totalWeight);
        for (LootEntry entry : loot) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return loot.get(loot.size() - 1);
    }

    private double[] resolveArea(World world, FileConfiguration config) {
        String mode = config.getString("area.mode", "BORDER").toUpperCase(Locale.ROOT);
        if ("FIXED".equals(mode)) {
            return new double[]{
                    config.getDouble("area.centre-x", 0.0D),
                    config.getDouble("area.centre-z", 0.0D),
                    Math.max(1.0D, config.getDouble("area.size", 200.0D) / 2.0D)};
        }
        org.bukkit.WorldBorder border = world.getWorldBorder();
        Location centre = border.getCenter();
        double half = Math.max(1.0D, border.getSize() / 2.0D - config.getDouble("area.inset", 8.0D));
        return new double[]{centre.getX(), centre.getZ(), half};
    }

    private World resolveWorld() {
        String configured = config().raw().getString("world", "");
        if (configured != null && !configured.isBlank()) {
            World named = Bukkit.getWorld(configured);
            if (named != null) {
                return named;
            }
        }
        WorldBorderModule border = plugin.modules().byType(WorldBorderModule.class);
        if (border != null && border.isEnabled()) {
            World world = border.targetWorld();
            if (world != null) {
                return world;
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }
}
