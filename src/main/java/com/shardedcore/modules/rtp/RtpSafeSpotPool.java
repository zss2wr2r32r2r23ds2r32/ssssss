package com.shardedcore.modules.rtp;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class RtpSafeSpotPool {

    private final RtpModule module;
    private final Map<UUID, ConcurrentLinkedDeque<Location>> pools = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Runnable> mainWork = new ConcurrentLinkedQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger generated = new AtomicInteger();
    private final Set<Biome> blocked = new HashSet<>();
    private BukkitTask refill;
    private BukkitTask minute;
    private BukkitTask drain;

    RtpSafeSpotPool(RtpModule module) {
        this.module = module;
        for (String name : module.config().getStringList("blocked-biomes")) {
            Biome biome = biome(name);
            if (biome != null) blocked.add(biome);
        }
    }

    void start() {
        int refillSeconds = Math.max(5, module.config().getInt("refill-seconds", 10));
        refill = Bukkit.getScheduler().runTaskTimerAsynchronously(module.plugin(), this::refill, 40L, refillSeconds * 20L);
        minute = Bukkit.getScheduler().runTaskTimerAsynchronously(module.plugin(), () -> generated.set(0), 20L * 60L, 20L * 60L);
        drain = Bukkit.getScheduler().runTaskTimer(module.plugin(), this::drain, 1L, 1L);
    }

    void shutdown() {
        if (refill != null) refill.cancel();
        if (minute != null) minute.cancel();
        if (drain != null) drain.cancel();
        mainWork.clear();
        pools.clear();
        inFlight.set(0);
    }

    Location poll(World world) {
        ConcurrentLinkedDeque<Location> pool = pools.get(world.getUID());
        if (pool == null) return null;
        Location loc;
        while ((loc = pool.pollFirst()) != null) {
            if (loc.getWorld() != null && loc.getWorld().equals(world)) return loc;
        }
        return null;
    }

    void request(World world, ConfigurationSection dest, Consumer<Location> done) {
        Location ready = poll(world);
        if (ready != null) {
            done.accept(ready);
            return;
        }
        ConcurrentLinkedDeque<Location> pool = pools.computeIfAbsent(world.getUID(), ignored -> new ConcurrentLinkedDeque<>());
        startSearch(world, dest, pool, Math.max(1, module.config().getInt("pool-size", 5)), loc ->
                Bukkit.getScheduler().runTask(module.plugin(), () -> done.accept(loc)));
    }

    private void drain() {
        int budget = Math.max(1, module.config().getInt("main-checks-per-tick", 1));
        for (int i = 0; i < budget; i++) {
            Runnable work = mainWork.poll();
            if (work == null) return;
            work.run();
        }
    }

    private void refill() {
        int size = module.config().getInt("pool-size", 5);
        int max = Math.max(1, module.config().getInt("concurrent-searches", 1));
        ConfigurationSection worlds = module.config().getConfigurationSection("worlds");
        if (worlds == null) return;
        for (String id : worlds.getKeys(false)) {
            if (inFlight.get() >= max) return;
            ConfigurationSection dest = worlds.getConfigurationSection(id);
            if (dest == null) continue;
            World world = module.resolveWorld(id, dest);
            if (world == null) continue;
            ConcurrentLinkedDeque<Location> pool = pools.computeIfAbsent(world.getUID(), ignored -> new ConcurrentLinkedDeque<>());
            if (pool.size() >= size) continue;
            startSearch(world, dest, pool, size);
        }
    }

    private void startSearch(World world, ConfigurationSection dest, ConcurrentLinkedDeque<Location> pool, int size) {
        startSearch(world, dest, pool, size, loc -> {
            if (loc != null && pool.size() < size) pool.offerLast(loc);
        });
    }

    private void startSearch(World world, ConfigurationSection dest, ConcurrentLinkedDeque<Location> pool, int size,
                             Consumer<Location> done) {
        inFlight.incrementAndGet();
        attempt(world, dest, module.config().getInt("attempts", 30), 0, loc -> {
            inFlight.decrementAndGet();
            done.accept(loc);
        });
    }

    private void attempt(World world, ConfigurationSection dest, int remaining, int used, Consumer<Location> done) {
        if (remaining <= 0) {
            mainWork.offer(() -> done.accept(null));
            return;
        }
        int radius = Math.max(16, dest.getInt("radius", 1000));
        int cx = dest.getInt("center-x", 0);
        int cz = dest.getInt("center-z", 0);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = cx + random.nextInt(-radius, radius + 1);
        int z = cz + random.nextInt(-radius, radius + 1);
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        boolean generatedChunk = world.isChunkGenerated(chunkX, chunkZ);
        boolean prefer = module.config().getBoolean("prefer-generated", true);
        int generatedAttempts = module.config().getInt("generated-attempts", 20);
        if (prefer && used < generatedAttempts && !generatedChunk) {
            attempt(world, dest, remaining - 1, used + 1, done);
            return;
        }
        if (!generatedChunk && !allowGenerate()) {
            attempt(world, dest, remaining - 1, used + 1, done);
            return;
        }
        world.getChunkAtAsync(chunkX, chunkZ).whenComplete((chunk, error) -> {
            Runnable next = () -> {
                Location loc = error == null && chunk != null ? findSafe(world, dest, x, z) : null;
                if (loc != null) {
                    done.accept(loc);
                    return;
                }
                attempt(world, dest, remaining - 1, used + 1, done);
            };
            mainWork.offer(next);
        });
    }

    private boolean allowGenerate() {
        int cap = module.config().getInt("generate-per-minute", 30);
        if (cap <= 0) return false;
        while (true) {
            int current = generated.get();
            if (current >= cap) return false;
            if (generated.compareAndSet(current, current + 1)) return true;
        }
    }

    private Location findSafe(World world, ConfigurationSection dest, int x, int z) {
        int minY = dest.getInt("min-y", world.getMinHeight());
        if (world.getEnvironment() == World.Environment.NETHER) {
            int maxY = dest.getInt("max-y", 120);
            for (int y = maxY; y >= minY; y--) {
                Location loc = standing(world, x, y, z);
                if (loc != null) return loc;
            }
            return null;
        }
        int highest = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (world.getEnvironment() == World.Environment.THE_END && highest < minY) return null;
        if (highest < minY) return null;
        return standing(world, x, highest, z);
    }

    private Location standing(World world, int x, int groundY, int z) {
        Block ground = world.getBlockAt(x, groundY, z);
        if (ground.isPassable()) {
            ground = world.getBlockAt(x, groundY - 1, z);
            groundY = ground.getY();
        }
        if (!safeGround(ground)) return null;
        Block feet = ground.getRelative(BlockFace.UP);
        Block head = feet.getRelative(BlockFace.UP);
        if (!passable(feet) || !passable(head)) return null;
        Biome biome = world.getBiome(x, groundY, z);
        if (blocked.contains(biome)) return null;
        Location loc = feet.getLocation().add(0.5, 0, 0.5);
        loc.setYaw(ThreadLocalRandom.current().nextFloat() * 360f);
        loc.setPitch(0f);
        return loc;
    }

    private boolean safeGround(Block ground) {
        Material type = ground.getType();
        if (!type.isSolid() || ground.isLiquid()) return false;
        return switch (type) {
            case LAVA, MAGMA_BLOCK, CACTUS, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE,
                 SWEET_BERRY_BUSH, POWDER_SNOW, WITHER_ROSE, COBWEB, END_PORTAL,
                 NETHER_PORTAL, KELP, KELP_PLANT -> false;
            default -> true;
        };
    }

    private boolean passable(Block block) {
        return block.isPassable() && !block.isLiquid() && block.getType() != Material.FIRE
                && block.getType() != Material.SOUL_FIRE && block.getType() != Material.LAVA;
    }

    private static Biome biome(String name) {
        if (name == null || name.isBlank()) return null;
        String key = name.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        return Registry.BIOME.get(NamespacedKey.minecraft(key));
    }
}
