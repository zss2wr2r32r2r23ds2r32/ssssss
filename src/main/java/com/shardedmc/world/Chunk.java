package com.shardedmc.world;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Memory-conscious chunk data structure with section-based storage.
 */
public final class Chunk {

    public enum State {
        UNLOADED,
        LOADING,
        GENERATING,
        LIGHTING,
        READY,
        UNLOADING
    }

    private final ChunkPos pos;
    private volatile State state = State.UNLOADED;
    private final short[][] sections;
    private final AtomicInteger entityCount = new AtomicInteger();
    private volatile long lastAccessTick;
    private volatile long loadStartNanos;
    private volatile long generationStartNanos;

    public Chunk(ChunkPos pos) {
        this.pos = pos;
        this.sections = new short[16][];
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new short[4096];
        }
    }

    public ChunkPos getPos() {
        return pos;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public short getBlock(int x, int y, int z) {
        if (y < 0 || y >= 256) {
            return 0;
        }
        int section = y >> 4;
        int index = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        return sections[section][index];
    }

    public void setBlock(int x, int y, int z, short blockId) {
        if (y < 0 || y >= 256) {
            return;
        }
        int section = y >> 4;
        int index = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        sections[section][index] = blockId;
    }

    public int getEntityCount() {
        return entityCount.get();
    }

    public void incrementEntityCount() {
        entityCount.incrementAndGet();
    }

    public void decrementEntityCount() {
        entityCount.decrementAndGet();
    }

    public long getLastAccessTick() {
        return lastAccessTick;
    }

    public void touch(long tick) {
        this.lastAccessTick = tick;
    }

    public long getLoadStartNanos() {
        return loadStartNanos;
    }

    public void markLoadStart() {
        this.loadStartNanos = System.nanoTime();
    }

    public long getGenerationStartNanos() {
        return generationStartNanos;
    }

    public void markGenerationStart() {
        this.generationStartNanos = System.nanoTime();
    }

    public byte[] serialize() {
        byte[] data = new byte[16 * 4096 * 2];
        int offset = 0;
        for (short[] section : sections) {
            for (short block : section) {
                data[offset++] = (byte) (block >> 8);
                data[offset++] = (byte) block;
            }
        }
        return data;
    }

    public void deserialize(byte[] data) {
        int offset = 0;
        for (int s = 0; s < sections.length; s++) {
            for (int i = 0; i < 4096; i++) {
                sections[s][i] = (short) (((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF));
                offset += 2;
            }
        }
    }

    public void clear() {
        for (short[] section : sections) {
            Arrays.fill(section, (short) 0);
        }
        entityCount.set(0);
        state = State.UNLOADED;
    }

    public long estimateMemoryBytes() {
        return 16L * 4096 * 2 + 64;
    }
}
