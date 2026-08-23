package com.shardedmc.entity;

import com.shardedmc.config.ShardedMCConfig;
import com.shardedmc.diagnostics.PerformanceMonitor;
import com.shardedmc.scheduler.ServerScheduler;
import com.shardedmc.sharding.ShardManager;
import com.shardedmc.world.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Entity manager with shard-based partitioning and concurrent ticking where safe.
 */
public final class EntityManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityManager.class);

    private final ShardedMCConfig config;
    private final ServerScheduler scheduler;
    private final ShardManager shardManager;
    private final PerformanceMonitor performanceMonitor;
    private final SpatialIndex spatialIndex;
    private final Map<UUID, Entity> entities = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Set<UUID>> chunkEntities = new ConcurrentHashMap<>();
    private final AtomicLong entityIdCounter = new AtomicLong(1);

    public EntityManager(
            ShardedMCConfig config,
            ServerScheduler scheduler,
            ShardManager shardManager,
            PerformanceMonitor performanceMonitor
    ) {
        this.config = config;
        this.scheduler = scheduler;
        this.shardManager = shardManager;
        this.performanceMonitor = performanceMonitor;
        this.spatialIndex = new SpatialIndex(config.getEntity().getSpatialIndexCellSize());
    }

    public void start() {
        LOGGER.info("EntityManager started (concurrent-ticking={})",
                config.getEntity().isConcurrentTicking());
    }

    public Entity spawn(EntityType type, double x, double y, double z) {
        UUID id = new UUID(0, entityIdCounter.getAndIncrement());
        Entity entity = new Entity(id, type, x, y, z);
        entities.put(id, entity);
        ChunkPos chunk = entity.getChunkPos();
        chunkEntities.computeIfAbsent(chunk, c -> ConcurrentHashMap.newKeySet()).add(id);
        spatialIndex.insert(entity);
        return entity;
    }

    public void remove(UUID id) {
        Entity entity = entities.remove(id);
        if (entity != null) {
            ChunkPos chunk = entity.getChunkPos();
            Set<UUID> set = chunkEntities.get(chunk);
            if (set != null) {
                set.remove(id);
            }
            spatialIndex.remove(entity);
        }
    }

    public void tick(long currentTick) {
        long start = System.nanoTime();

        if (config.getEntity().isConcurrentTicking()) {
            List<Entity> activeEntities = entities.values().stream()
                    .filter(Entity::isNearPlayer)
                    .toList();
            List<Entity> inactiveEntities = entities.values().stream()
                    .filter(e -> !e.isNearPlayer())
                    .toList();

            var futures = activeEntities.stream()
                    .map(e -> scheduler.submit(ServerScheduler.PoolType.ENTITY, () -> tickEntity(e, currentTick)))
                    .toList();
            futures.forEach(f -> {
                try {
                    f.join();
                } catch (Exception ex) {
                    LOGGER.error("Entity tick failed", ex);
                }
            });

            if (currentTick % config.getEntity().getInactiveTickInterval() == 0) {
                inactiveEntities.forEach(e -> tickEntity(e, currentTick));
            }
        } else {
            entities.values().forEach(e -> tickEntity(e, currentTick));
        }

        performanceMonitor.recordEntityTickCost(System.nanoTime() - start);
    }

    private void tickEntity(Entity entity, long tick) {
        entity.tick(tick);
        spatialIndex.update(entity);
    }

    public Collection<Entity> getNearbyEntities(double x, double y, double z, double radius) {
        return spatialIndex.query(x, y, z, radius);
    }

    public int getEntityCount() {
        return entities.size();
    }

    public Map<ChunkPos, Set<UUID>> getChunkEntities() {
        return Map.copyOf(chunkEntities);
    }

    public void shutdown() {
        entities.clear();
        chunkEntities.clear();
        spatialIndex.clear();
    }
}
