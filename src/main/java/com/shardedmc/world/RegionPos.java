package com.shardedmc.world;

/**
 * Region/shard coordinate derived from chunk coordinates.
 */
public record RegionPos(int x, int z) {

    public String key() {
        return x + ":" + z;
    }

    public boolean contains(ChunkPos chunk, int regionSize) {
        RegionPos chunkRegion = chunk.region(regionSize);
        return chunkRegion.x == x && chunkRegion.z == z;
    }
}
