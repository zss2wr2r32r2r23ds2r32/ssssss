package com.shardedmc.generation;

import java.util.Random;

/**
 * Cache for expensive noise calculations reused across generation stages.
 */
public final class NoiseCache {

    private final double[] cache;
    private final int mask;

    public NoiseCache(int size) {
        int capacity = 1;
        while (capacity < size) {
            capacity <<= 1;
        }
        this.cache = new double[capacity];
        this.mask = capacity - 1;
    }

    public double sample(int x, int z, Random random) {
        int key = (x * 734287 ^ z * 912271) & mask;
        double cached = cache[key];
        if (cached != 0.0) {
            return cached;
        }
        double value = random.nextGaussian();
        cache[key] = value;
        return value;
    }

    public void clear() {
        java.util.Arrays.fill(cache, 0.0);
    }
}
