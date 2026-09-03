package com.shardedcore.eventcore.modules;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.module.EventModule;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Clears everything inside the world border down to the bedrock floor.
 *
 * <p>A naive implementation reads and writes tens of millions of blocks on the
 * main thread and stalls the server for minutes. This one splits the work in
 * three:</p>
 *
 * <ol>
 *   <li><b>Fetch</b> — chunks are pulled with {@code getChunkAtAsync} and
 *       snapshotted on the main thread. Only a small, configurable number of
 *       snapshots are ever in flight, which bounds memory use.</li>
 *   <li><b>Scan</b> — an async worker walks each snapshot from its heightmap
 *       down to the floor and packs the coordinates of the non-air blocks into
 *       a dense {@code int[]}. All the reading and filtering, which is the bulk
 *       of the CPU cost, happens off the main thread.</li>
 *   <li><b>Apply</b> — the main thread pops those arrays and writes air with
 *       physics disabled, stopping as soon as it has spent its per-tick time
 *       budget. Writes are ordered top-down so skylight propagates once per
 *       column instead of once per block.</li>
 * </ol>
 *
 * <p>Because the applier is budgeted, the server keeps a full tick rate no
 * matter how large the border is. Operators who genuinely want a single-tick
 * clear can set {@code instant: true} and accept the stall.</p>
 */
public final class BedrockDropModule extends EventModule {

    /** One chunk's worth of positions to clear, encoded as {@code (y+64)<<8 | lx<<4 | lz}. */
    private record Payload(int chunkX, int chunkZ, int[] encoded) {
    }

    private static final int Y_BIAS = 64;
    private static final int BUDGET_CHECK_INTERVAL = 512;

    private final BlockData air = Material.AIR.createBlockData();

    private Job job;

    public BedrockDropModule(ShardedEventCore plugin) {
        super(plugin, "bedrockdrop", "Clears the bordered area down to bedrock without TPS loss.");
    }

    @Override
    protected boolean hasListeners() {
        return false;
    }

    @Override
    protected void onModuleDisable() {
        cancel();
    }

    public boolean isRunning() {
        return job != null;
    }

    public int progressPercent() {
        return job == null ? 100 : job.progressPercent();
    }

    public void cancel() {
        if (job != null) {
            job.stop();
            job = null;
        }
    }

    /**
     * Starts a clear over the current border area.
     *
     * @param onComplete run on the main thread with the number of blocks removed
     * @return false when a clear is already running or no world is available
     */
    public boolean start(Consumer<Long> onComplete) {
        if (job != null) {
            return false;
        }
        World world = resolveWorld();
        if (world == null) {
            return false;
        }

        FileConfiguration config = config().raw();
        Bounds bounds = resolveBounds(world, config);
        if (bounds == null) {
            return false;
        }

        job = new Job(world, bounds, readPreserved(config), config, onComplete);
        job.begin();
        return true;
    }

    private World resolveWorld() {
        WorldBorderModule border = plugin.modules().byType(WorldBorderModule.class);
        if (border != null && border.isEnabled()) {
            World world = border.targetWorld();
            if (world != null) {
                return world;
            }
        }
        String configured = config().raw().getString("world", "");
        if (configured != null && !configured.isBlank()) {
            World named = Bukkit.getWorld(configured);
            if (named != null) {
                return named;
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    /** Reads the materials that survive the clear, e.g. the bedrock floor itself. */
    private Set<Material> readPreserved(FileConfiguration config) {
        Set<Material> preserved = EnumSet.noneOf(Material.class);
        for (String raw : config.getStringList("preserve")) {
            Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
            if (material != null) {
                preserved.add(material);
            }
        }
        return preserved;
    }

    private record Bounds(int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {

        int chunkMinX() {
            return minX >> 4;
        }

        int chunkMaxX() {
            return maxX >> 4;
        }

        int chunkMinZ() {
            return minZ >> 4;
        }

        int chunkMaxZ() {
            return maxZ >> 4;
        }

        int chunkCount() {
            return (chunkMaxX() - chunkMinX() + 1) * (chunkMaxZ() - chunkMinZ() + 1);
        }
    }

    /**
     * Works out the box to clear. {@code BORDER} mode reads the live border size,
     * which is what makes "if the size is 200 x 200 it breaks everything inside"
     * work without any extra configuration.
     */
    private Bounds resolveBounds(World world, FileConfiguration config) {
        String mode = config.getString("area.mode", "BORDER").toUpperCase(Locale.ROOT);

        double centreX;
        double centreZ;
        double size;

        if ("FIXED".equals(mode)) {
            centreX = config.getDouble("area.centre-x", 0.0D);
            centreZ = config.getDouble("area.centre-z", 0.0D);
            size = config.getDouble("area.size", 200.0D);
        } else {
            org.bukkit.WorldBorder border = world.getWorldBorder();
            Location centre = border.getCenter();
            centreX = centre.getX();
            centreZ = centre.getZ();
            size = border.getSize();
        }

        double inset = config.getDouble("area.inset", 0.0D);
        double half = Math.max(1.0D, size / 2.0D - inset);
        double maxSize = config.getDouble("area.max-size", 2000.0D);
        if (maxSize > 0.0D && half * 2.0D > maxSize) {
            half = maxSize / 2.0D;
        }

        int minY = config.getInt("min-y", -63);
        int maxY = config.getInt("max-y", Integer.MIN_VALUE);
        if (maxY == Integer.MIN_VALUE) {
            maxY = world.getMaxHeight() - 1;
        }
        minY = Math.max(world.getMinHeight(), minY);
        maxY = Math.min(world.getMaxHeight() - 1, maxY);
        if (minY > maxY) {
            return null;
        }

        return new Bounds(
                (int) Math.floor(centreX - half),
                (int) Math.ceil(centreX + half),
                (int) Math.floor(centreZ - half),
                (int) Math.ceil(centreZ + half),
                minY,
                maxY);
    }

    /** Teleports players a little above the floor so nobody is left inside a wall. */
    public int dropPlayers() {
        FileConfiguration config = config().raw();
        if (!config.getBoolean("drop-players", false)) {
            return 0;
        }
        World world = resolveWorld();
        if (world == null) {
            return 0;
        }
        int y = config.getInt("min-y", -63) + config.getInt("drop-players-offset", 1);
        int moved = 0;
        for (Player player : world.getPlayers()) {
            Location target = player.getLocation();
            if (target.getY() <= y) {
                continue;
            }
            target.setY(y);
            player.teleportAsync(target);
            moved++;
        }
        return moved;
    }

    // -------------------------------------------------------------------- job

    /** Mutable state for a single in-progress clear. */
    private final class Job {

        private final World world;
        private final Bounds bounds;
        private final Set<Material> preserved;
        private final boolean useHeightmap;
        private final boolean instant;
        private final boolean generateChunks;
        private final long budgetNanos;
        private final int maxBlocksPerTick;
        private final int maxPendingChunks;
        private final int fetchesPerTick;
        private final Consumer<Long> onComplete;

        private final Queue<Payload> ready = new ConcurrentLinkedQueue<>();
        private final AtomicInteger inFlight = new AtomicInteger();

        private BukkitTask applier;
        private int cursorX;
        private int cursorZ;
        private boolean fetchDone;
        private boolean stopped;

        private Payload current;
        private int currentIndex;
        private Chunk currentChunk;

        private long blocksCleared;
        private int chunksDone;

        private Job(World world, Bounds bounds, Set<Material> preserved, FileConfiguration config,
                    Consumer<Long> onComplete) {
            this.world = world;
            this.bounds = bounds;
            this.preserved = preserved;
            this.onComplete = onComplete;
            this.useHeightmap = config.getBoolean("use-heightmap", true);
            this.instant = config.getBoolean("instant", false);
            this.generateChunks = config.getBoolean("generate-missing-chunks", false);
            this.budgetNanos = Math.max(1L, config.getLong("max-millis-per-tick", 8L)) * 1_000_000L;
            this.maxBlocksPerTick = Math.max(1024, config.getInt("max-blocks-per-tick", 120_000));
            this.maxPendingChunks = Math.max(1, config.getInt("max-pending-chunks", 8));
            this.fetchesPerTick = Math.max(1, config.getInt("chunk-fetches-per-tick", 4));
            this.cursorX = bounds.chunkMinX();
            this.cursorZ = bounds.chunkMinZ();
        }

        void begin() {
            applier = track(Bukkit.getScheduler().runTaskTimer(plugin, this::pump, 1L, 1L));
        }

        void stop() {
            stopped = true;
            if (applier != null) {
                applier.cancel();
                applier = null;
            }
            ready.clear();
            current = null;
            currentChunk = null;
        }

        int progressPercent() {
            int total = bounds.chunkCount();
            return total <= 0 ? 100 : Math.min(100, chunksDone * 100 / total);
        }

        private void pump() {
            if (stopped) {
                return;
            }
            fetch();
            apply();

            if (fetchDone && inFlight.get() == 0 && ready.isEmpty() && current == null) {
                long cleared = blocksCleared;
                Consumer<Long> callback = onComplete;
                stop();
                if (job == this) {
                    job = null;
                }
                if (callback != null) {
                    callback.accept(cleared);
                }
            }
        }

        /** Keeps a small number of snapshots queued without letting memory grow. */
        private void fetch() {
            if (fetchDone) {
                return;
            }
            int requests = 0;
            while (requests < fetchesPerTick && ready.size() + inFlight.get() < maxPendingChunks) {
                if (cursorZ > bounds.chunkMaxZ()) {
                    fetchDone = true;
                    return;
                }
                int chunkX = cursorX;
                int chunkZ = cursorZ;
                if (++cursorX > bounds.chunkMaxX()) {
                    cursorX = bounds.chunkMinX();
                    cursorZ++;
                }
                requests++;

                if (!generateChunks && !world.isChunkGenerated(chunkX, chunkZ)) {
                    chunksDone++;
                    continue;
                }
                inFlight.incrementAndGet();
                world.getChunkAtAsync(chunkX, chunkZ, generateChunks).thenAccept(chunk -> {
                    if (stopped || chunk == null) {
                        inFlight.decrementAndGet();
                        return;
                    }
                    // Snapshotting must happen on the main thread; scanning must not.
                    ChunkSnapshot snapshot = chunk.getChunkSnapshot(useHeightmap, false, false);
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> scan(snapshot, chunkX, chunkZ));
                }).exceptionally(throwable -> {
                    inFlight.decrementAndGet();
                    return null;
                });
            }
        }

        /** Off-thread: turn a snapshot into the dense list of positions to clear. */
        private void scan(ChunkSnapshot snapshot, int chunkX, int chunkZ) {
            try {
                int baseX = chunkX << 4;
                int baseZ = chunkZ << 4;
                int localMinX = Math.max(0, bounds.minX() - baseX);
                int localMaxX = Math.min(15, bounds.maxX() - baseX);
                int localMinZ = Math.max(0, bounds.minZ() - baseZ);
                int localMaxZ = Math.min(15, bounds.maxZ() - baseZ);
                if (localMinX > localMaxX || localMinZ > localMaxZ) {
                    return;
                }

                int[] buffer = new int[4096];
                int count = 0;
                int minHeight = world.getMinHeight();

                // Descending Y so the applier clears each column from the top down.
                for (int y = bounds.maxY(); y >= bounds.minY(); y--) {
                    // An all-air 16-block section can be skipped without reading it.
                    if ((y & 0xF) == 0xF && isSectionEmpty(snapshot, y, minHeight)) {
                        y -= 15;
                        continue;
                    }
                    int encodedY = (y + Y_BIAS) << 8;
                    for (int localX = localMinX; localX <= localMaxX; localX++) {
                        for (int localZ = localMinZ; localZ <= localMaxZ; localZ++) {
                            if (useHeightmap && y > snapshot.getHighestBlockYAt(localX, localZ)) {
                                continue;
                            }
                            Material type = snapshot.getBlockType(localX, y, localZ);
                            if (type.isAir() || preserved.contains(type)) {
                                continue;
                            }
                            if (count == buffer.length) {
                                buffer = java.util.Arrays.copyOf(buffer, buffer.length << 1);
                            }
                            buffer[count++] = encodedY | (localX << 4) | localZ;
                        }
                    }
                }

                if (count > 0) {
                    ready.add(new Payload(chunkX, chunkZ, count == buffer.length
                            ? buffer : java.util.Arrays.copyOf(buffer, count)));
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Bedrock drop scan failed for chunk "
                        + chunkX + "," + chunkZ + ": " + exception);
            } finally {
                inFlight.decrementAndGet();
            }
        }

        /**
         * Whether the 16-block section containing {@code y} is entirely air.
         * Section indices count up from the world's lowest section.
         */
        private boolean isSectionEmpty(ChunkSnapshot snapshot, int y, int minHeight) {
            int index = (y - minHeight) >> 4;
            if (index < 0) {
                return false;
            }
            try {
                return snapshot.isSectionEmpty(index);
            } catch (IndexOutOfBoundsException exception) {
                return false;
            }
        }

        /** Main thread: write air until the tick budget or block cap is spent. */
        private void apply() {
            long deadline = System.nanoTime() + budgetNanos;
            int written = 0;
            int sinceCheck = 0;

            while (true) {
                if (current == null) {
                    current = ready.poll();
                    if (current == null) {
                        return;
                    }
                    currentIndex = 0;
                    currentChunk = world.getChunkAt(current.chunkX(), current.chunkZ());
                }

                int[] encoded = current.encoded();
                while (currentIndex < encoded.length) {
                    int value = encoded[currentIndex++];
                    int y = (value >>> 8) - Y_BIAS;
                    Block block = currentChunk.getBlock((value >> 4) & 0xF, y, value & 0xF);
                    if (!block.getType().isAir()) {
                        block.setBlockData(air, false);
                        blocksCleared++;
                    }
                    written++;

                    if (instant) {
                        continue;
                    }
                    // nanoTime is not free, so only sample it periodically.
                    if (++sinceCheck >= BUDGET_CHECK_INTERVAL) {
                        sinceCheck = 0;
                        if (written >= maxBlocksPerTick || System.nanoTime() >= deadline) {
                            return;
                        }
                    }
                }

                chunksDone++;
                current = null;
                currentChunk = null;
                if (!instant && (written >= maxBlocksPerTick || System.nanoTime() >= deadline)) {
                    return;
                }
            }
        }
    }
}
