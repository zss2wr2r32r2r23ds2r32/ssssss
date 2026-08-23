package com.shardedmc.sharding;

import com.shardedmc.world.ChunkPos;
import com.shardedmc.world.RegionPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single shard responsible for a region of chunks.
 */
public final class RegionShard {

    private final RegionPos region;
    private final int regionSize;
    private final Set<ChunkPos> loadedChunks = ConcurrentHashMap.newKeySet();
    private final AtomicInteger pendingTasks = new AtomicInteger();
    private final AtomicLong lastProcessedTick = new AtomicLong();
    private final AtomicLong totalProcessedTasks = new AtomicLong();
    private volatile long tickCostNanos;

    public RegionShard(RegionPos region, int regionSize) {
        this.region = region;
        this.regionSize = regionSize;
    }

    public RegionPos getRegion() {
        return region;
    }

    public int getRegionSize() {
        return regionSize;
    }

    public void registerChunk(ChunkPos pos) {
        loadedChunks.add(pos);
    }

    public void unregisterChunk(ChunkPos pos) {
        loadedChunks.remove(pos);
    }

    public Set<ChunkPos> getLoadedChunks() {
        return Set.copyOf(loadedChunks);
    }

    public int getLoadedChunkCount() {
        return loadedChunks.size();
    }

    public void enqueueTask(Runnable task) {
        pendingTasks.incrementAndGet();
        try {
            task.run();
            totalProcessedTasks.incrementAndGet();
        } finally {
            pendingTasks.decrementAndGet();
        }
    }

    public boolean hasPendingWork() {
        return pendingTasks.get() > 0 || !loadedChunks.isEmpty();
    }

    public void processTick(long tick) {
        long start = System.nanoTime();
        lastProcessedTick.set(tick);
        tickCostNanos = System.nanoTime() - start;
    }

    public long getLastProcessedTick() {
        return lastProcessedTick.get();
    }

    public long getTotalProcessedTasks() {
        return totalProcessedTasks.get();
    }

    public long getTickCostNanos() {
        return tickCostNanos;
    }

    public int getPendingTaskCount() {
        return pendingTasks.get();
    }
}
