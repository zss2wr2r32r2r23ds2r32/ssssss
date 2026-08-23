package com.shardedmc.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkPosTest {

    @Test
    void regionCalculationUsesFloorDivision() {
        ChunkPos pos = new ChunkPos(-1, -1);
        assertEquals(-1, pos.region(8).x());
        assertEquals(-1, pos.region(8).z());
    }

    @Test
    void longEncodingRoundTrip() {
        ChunkPos original = new ChunkPos(12345, -6789);
        assertEquals(original, ChunkPos.fromLong(original.asLong()));
    }

    @Test
    void manhattanDistance() {
        ChunkPos a = new ChunkPos(0, 0);
        ChunkPos b = new ChunkPos(3, 4);
        assertEquals(7, a.manhattanDistance(b));
    }
}
