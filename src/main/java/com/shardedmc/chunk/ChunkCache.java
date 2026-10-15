package com.shardedmc.chunk;

import com.shardedmc.world.Chunk;
import com.shardedmc.world.ChunkPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * LRU chunk cache with configurable capacity and eviction.
 */
public final class ChunkCache {

    private final int maxSize;
    private final LinkedHashMap<ChunkPos, Chunk> cache;

    public ChunkCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ChunkPos, Chunk> eldest) {
                return size() > ChunkCache.this.maxSize;
            }
        };
    }

    public synchronized Optional<Chunk> get(ChunkPos pos) {
        Chunk chunk = cache.get(pos);
        return Optional.ofNullable(chunk);
    }

    public synchronized void put(ChunkPos pos, Chunk chunk) {
        cache.put(pos, chunk);
    }

    public synchronized Chunk remove(ChunkPos pos) {
        return cache.remove(pos);
    }

    public synchronized int size() {
        return cache.size();
    }

    public synchronized Map<ChunkPos, Chunk> entries() {
        return Map.copyOf(cache);
    }

    public synchronized long estimateMemoryBytes() {
        return cache.values().stream().mapToLong(Chunk::estimateMemoryBytes).sum();
    }
}
