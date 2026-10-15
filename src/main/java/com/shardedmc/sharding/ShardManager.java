package com.shardedmc.sharding;

import com.shardedmc.config.ShardedMCConfig;
import com.shardedmc.scheduler.ServerScheduler;
import com.shardedmc.world.ChunkPos;
import com.shardedmc.world.RegionPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages region-based sharding with ownership locking and independent shard processing.
 */
public final class ShardManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShardManager.class);

    private final ShardedMCConfig config;
    private final ServerScheduler scheduler;
    private final RegionLock regionLock;
    private final Map<RegionPos, RegionShard> shards = new ConcurrentHashMap<>();
    private final AtomicLong tickCounter = new AtomicLong();

    public ShardManager(ShardedMCConfig config, ServerScheduler scheduler) {
        this.config = config;
        this.scheduler = scheduler;
        this.regionLock = new RegionLock(config.getSharding().getLockTimeoutMs());
    }

    public RegionShard getOrCreateShard(ChunkPos chunk) {
        RegionPos region = chunk.region(config.getSharding().getRegionSize());
        return shards.computeIfAbsent(region, r -> new RegionShard(r, config.getSharding().getRegionSize()));
    }

    public RegionShard getShard(RegionPos region) {
        return shards.get(region);
    }

    public void processTick(long tick) {
        if (!config.getSharding().isEnabled()) {
            return;
        }
        tickCounter.set(tick);
        for (RegionShard shard : shards.values()) {
            if (shard.hasPendingWork()) {
                scheduler.submit(ServerScheduler.PoolType.SHARD, () -> processShard(shard, tick));
            }
        }
    }

    private void processShard(RegionShard shard, long tick) {
        RegionPos region = shard.getRegion();
        if (!regionLock.tryLock(region)) {
            return;
        }
        try {
            shard.processTick(tick);
        } catch (Exception e) {
            LOGGER.error("Shard processing failed for region {}", region.key(), e);
        } finally {
            regionLock.unlock(region);
        }
    }

    public boolean executeOnShard(ChunkPos chunk, Runnable task) {
        RegionShard shard = getOrCreateShard(chunk);
        RegionPos region = shard.getRegion();
        Runnable guarded = () -> {
            if (!regionLock.tryLock(region)) {
                return;
            }
            try {
                task.run();
            } finally {
                regionLock.unlock(region);
            }
        };
        if (config.getSharding().isEnabled()) {
            scheduler.submit(ServerScheduler.PoolType.SHARD, guarded);
        } else {
            guarded.run();
        }
        return true;
    }

    public Map<RegionPos, RegionShard> getShards() {
        return Map.copyOf(shards);
    }

    public int getShardCount() {
        return shards.size();
    }

    public RegionLock getRegionLock() {
        return regionLock;
    }
}
