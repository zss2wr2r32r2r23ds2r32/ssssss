package com.shardedmc.sharding;

import com.shardedmc.world.RegionPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Region ownership locking to prevent race conditions during concurrent shard processing.
 */
public final class RegionLock {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegionLock.class);

    private final long timeoutMs;
    private final Map<RegionPos, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Map<RegionPos, Thread> owners = new ConcurrentHashMap<>();

    public RegionLock(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean tryLock(RegionPos region) {
        ReentrantLock lock = locks.computeIfAbsent(region, r -> new ReentrantLock());
        try {
            if (lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
                owners.put(region, Thread.currentThread());
                return true;
            }
            LOGGER.warn("Failed to acquire region lock for {} within {}ms", region.key(), timeoutMs);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void unlock(RegionPos region) {
        ReentrantLock lock = locks.get(region);
        if (lock != null && lock.isHeldByCurrentThread()) {
            owners.remove(region);
            lock.unlock();
        }
    }

    public boolean isLocked(RegionPos region) {
        ReentrantLock lock = locks.get(region);
        return lock != null && lock.isLocked();
    }

    public int getLockCount() {
        return (int) locks.values().stream().filter(ReentrantLock::isLocked).count();
    }
}
