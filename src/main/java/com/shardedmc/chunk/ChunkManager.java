package com.shardedmc.chunk;

import com.shardedmc.config.ShardedMCConfig;
import com.shardedmc.diagnostics.PerformanceMonitor;
import com.shardedmc.generation.ChunkGenerator;
import com.shardedmc.generation.GenerationPipeline;
import com.shardedmc.scheduler.PlayerBasedScheduler;
import com.shardedmc.scheduler.ServerScheduler;
import com.shardedmc.sharding.RegionShard;
import com.shardedmc.sharding.ShardManager;
import com.shardedmc.world.Chunk;
import com.shardedmc.world.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates async chunk loading, generation, caching, and unloading.
 */
public final class ChunkManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkManager.class);

    private final Path serverRoot;
    private final ShardedMCConfig config;
    private final ServerScheduler scheduler;
    private final ShardManager shardManager;
    private final PerformanceMonitor performanceMonitor;
    private final ChunkCache cache;
    private final AsyncChunkLoader loader;
    private final ChunkSerializer serializer;
    private final GenerationPipeline generationPipeline;
    private final PlayerBasedScheduler playerScheduler;
    private final Set<Long> inFlightLoads = ConcurrentHashMap.newKeySet();
    private final Map<Long, CompletableFuture<Chunk>> pendingLoads = new ConcurrentHashMap<>();

    public ChunkManager(
            Path serverRoot,
            ShardedMCConfig config,
            ServerScheduler scheduler,
            ShardManager shardManager,
            PerformanceMonitor performanceMonitor
    ) {
        this.serverRoot = serverRoot;
        this.config = config;
        this.scheduler = scheduler;
        this.shardManager = shardManager;
        this.performanceMonitor = performanceMonitor;
        this.cache = new ChunkCache(config.getChunks().getCacheSize());
        this.serializer = new ChunkSerializer(serverRoot);
        this.loader = new AsyncChunkLoader(scheduler, serializer, performanceMonitor);
        ChunkGenerator generator = new ChunkGenerator(config);
        this.generationPipeline = new GenerationPipeline(scheduler, generator, performanceMonitor);
        this.playerScheduler = new PlayerBasedScheduler();
    }

    public void start() {
        LOGGER.info("ChunkManager started (async-loading={}, cache-size={})",
                config.getChunks().isAsyncLoading(), config.getChunks().getCacheSize());
    }

    public CompletableFuture<Chunk> loadChunk(int x, int z) {
        ChunkPos pos = new ChunkPos(x, z);
        long key = pos.asLong();

        Optional<Chunk> cached = cache.get(pos);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }

        CompletableFuture<Chunk> existing = pendingLoads.get(key);
        if (existing != null) {
            return existing;
        }

        if (!inFlightLoads.add(key)) {
            return pendingLoads.getOrDefault(key, CompletableFuture.completedFuture(null));
        }

        playerScheduler.enqueueLoad(x, z, 5);

        CompletableFuture<Chunk> future = loadInternal(pos);
        pendingLoads.put(key, future);
        future.whenComplete((chunk, ex) -> {
            inFlightLoads.remove(key);
            pendingLoads.remove(key);
            if (chunk != null) {
                cache.put(pos, chunk);
                RegionShard shard = shardManager.getOrCreateShard(pos);
                shard.registerChunk(pos);
            }
        });
        return future;
    }

    private CompletableFuture<Chunk> loadInternal(ChunkPos pos) {
        Chunk chunk = new Chunk(pos);
        chunk.markLoadStart();
        chunk.setState(Chunk.State.LOADING);

        CompletableFuture<Chunk> loadFuture;
        if (config.getChunks().isAsyncLoading()) {
            loadFuture = loader.loadAsync(pos, chunk);
        } else {
            loadFuture = CompletableFuture.supplyAsync(() -> loader.loadSync(pos, chunk),
                    scheduler.poolFor(ServerScheduler.PoolType.MAIN));
        }

        return loadFuture.thenCompose(loaded -> {
            if (loaded.getState() == Chunk.State.READY) {
                return CompletableFuture.completedFuture(loaded);
            }
            if (config.getChunks().isAsyncGeneration()) {
                loaded.setState(Chunk.State.GENERATING);
                loaded.markGenerationStart();
                return generationPipeline.generate(loaded).thenCompose(generated ->
                        applyLighting(generated));
            }
            return applyLighting(loaded);
        });
    }

    private CompletableFuture<Chunk> applyLighting(Chunk chunk) {
        chunk.setState(Chunk.State.LIGHTING);
        return scheduler.submit(ServerScheduler.PoolType.LIGHTING, () -> {
            // Lighting pass placeholder — real implementation would compute skylight/blocklight
            chunk.setState(Chunk.State.READY);
            return chunk;
        });
    }

    public void prefetchAround(double x, double z, int radius) {
        if (!config.getChunks().isPrefetch()) {
            return;
        }
        int centerX = (int) Math.floor(x / 16.0);
        int centerZ = (int) Math.floor(z / 16.0);
        int prefetchRadius = Math.min(radius, config.getChunks().getPrefetchRadius());
        for (int dx = -prefetchRadius; dx <= prefetchRadius; dx++) {
            for (int dz = -prefetchRadius; dz <= prefetchRadius; dz++) {
                loadChunk(centerX + dx, centerZ + dz);
            }
        }
    }

    public void processTick(long tick) {
        Optional<PlayerBasedScheduler.ChunkLoadRequest> request = playerScheduler.pollHighestPriority();
        request.ifPresent(r -> loadChunk(r.chunkX(), r.chunkZ()));
    }

    public void processUnloads(long tick) {
        int unloadDelay = config.getChunks().getUnloadDelay();
        for (Map.Entry<ChunkPos, Chunk> entry : cache.entries().entrySet()) {
            Chunk chunk = entry.getValue();
            if (chunk.getState() == Chunk.State.READY
                    && chunk.getEntityCount() == 0
                    && tick - chunk.getLastAccessTick() > unloadDelay) {
                unloadChunk(entry.getKey());
            }
        }
    }

    public void unloadChunk(ChunkPos pos) {
        Chunk chunk = cache.remove(pos);
        if (chunk == null) {
            return;
        }
        chunk.setState(Chunk.State.UNLOADING);
        scheduler.submit(ServerScheduler.PoolType.CHUNK_IO, () -> {
            try {
                serializer.save(pos, chunk);
            } catch (Exception e) {
                LOGGER.error("Failed to save chunk {}", pos, e);
            }
        }).thenRun(() -> {
            RegionShard shard = shardManager.getShard(pos.region(config.getSharding().getRegionSize()));
            if (shard != null) {
                shard.unregisterChunk(pos);
            }
            chunk.clear();
        });
    }

    public void flushAndShutdown() {
        LOGGER.info("Flushing {} cached chunks...", cache.size());
        for (Map.Entry<ChunkPos, Chunk> entry : cache.entries().entrySet()) {
            try {
                serializer.save(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                LOGGER.error("Failed to flush chunk {}", entry.getKey(), e);
            }
        }
    }

    public ChunkCache getCache() {
        return cache;
    }

    public PlayerBasedScheduler getPlayerScheduler() {
        return playerScheduler;
    }

    public int getInFlightLoadCount() {
        return inFlightLoads.size();
    }
}
