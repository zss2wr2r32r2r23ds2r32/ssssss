package com.shardedmc.memory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * Generic object pool for hot-path allocations.
 */
public final class ObjectPool<T> {

    private final Queue<T> pool = new ConcurrentLinkedQueue<>();
    private final Supplier<T> factory;
    private final int maxSize;

    public ObjectPool(Supplier<T> factory, int maxSize) {
        this.factory = factory;
        this.maxSize = maxSize;
    }

    public T acquire() {
        T obj = pool.poll();
        return obj != null ? obj : factory.get();
    }

    public void release(T obj) {
        if (pool.size() < maxSize) {
            pool.offer(obj);
        }
    }

    public int size() {
        return pool.size();
    }
}
