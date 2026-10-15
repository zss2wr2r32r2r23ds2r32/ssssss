package com.shardedmc.world;

import java.util.Objects;

/**
 * Immutable chunk coordinate pair.
 */
public record ChunkPos(int x, int z) {

    public long asLong() {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static ChunkPos fromLong(long value) {
        return new ChunkPos((int) (value >> 32), (int) value);
    }

    public RegionPos region(int regionSize) {
        return new RegionPos(
                Math.floorDiv(x, regionSize),
                Math.floorDiv(z, regionSize)
        );
    }

    @Override
    public String toString() {
        return "Chunk[" + x + ", " + z + "]";
    }

    public int manhattanDistance(ChunkPos other) {
        return Math.abs(x - other.x) + Math.abs(z - other.z);
    }

    public boolean equalsNormalized(Object obj) {
        return obj instanceof ChunkPos cp && x == cp.x && z == cp.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }
}
