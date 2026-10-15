package com.shardedmc.chunk;

import com.shardedmc.world.Chunk;
import com.shardedmc.world.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkCacheTest {

    @Test
    void evictsOldestWhenOverCapacity() {
        ChunkCache cache = new ChunkCache(2);
        Chunk a = new Chunk(new ChunkPos(0, 0));
        Chunk b = new Chunk(new ChunkPos(1, 0));
        Chunk c = new Chunk(new ChunkPos(2, 0));

        cache.put(new ChunkPos(0, 0), a);
        cache.put(new ChunkPos(1, 0), b);
        cache.get(new ChunkPos(0, 0)); // touch a
        cache.put(new ChunkPos(2, 0), c);

        assertTrue(cache.get(new ChunkPos(0, 0)).isPresent());
        assertFalse(cache.get(new ChunkPos(1, 0)).isPresent());
        assertTrue(cache.get(new ChunkPos(2, 0)).isPresent());
    }

    @Test
    void tracksMemoryEstimate() {
        ChunkCache cache = new ChunkCache(10);
        cache.put(new ChunkPos(0, 0), new Chunk(new ChunkPos(0, 0)));
        assertTrue(cache.estimateMemoryBytes() > 0);
    }
}
