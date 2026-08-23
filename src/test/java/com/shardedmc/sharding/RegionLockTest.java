package com.shardedmc.sharding;

import com.shardedmc.world.RegionPos;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RegionLockTest {

    @Test
    void exclusiveLockPreventsConcurrentAccess() throws InterruptedException {
        RegionLock lock = new RegionLock(1000);
        RegionPos region = new RegionPos(0, 0);

        assertTrue(lock.tryLock(region));

        AtomicBoolean secondAcquired = new AtomicBoolean(false);
        Thread t = new Thread(() -> secondAcquired.set(lock.tryLock(region)));
        t.start();
        t.join(200);

        assertFalse(secondAcquired.get());
        lock.unlock(region);

        assertTrue(lock.tryLock(region));
        lock.unlock(region);
    }

    @Test
    void lockReleasedAfterUnlock() throws InterruptedException {
        RegionLock lock = new RegionLock(1000);
        RegionPos region = new RegionPos(1, 1);

        assertTrue(lock.tryLock(region));
        lock.unlock(region);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean acquired = new AtomicBoolean(false);
        Thread t = new Thread(() -> {
            acquired.set(lock.tryLock(region));
            latch.countDown();
        });
        t.start();
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue(acquired.get());
        lock.unlock(region);
    }
}
