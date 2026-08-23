package com.shardedmc.scheduler;

import com.shardedmc.chunk.ChunkManager;
import com.shardedmc.config.ShardedMCConfig;
import com.shardedmc.diagnostics.PerformanceMonitor;
import com.shardedmc.entity.EntityManager;
import com.shardedmc.network.NetworkManager;
import com.shardedmc.sharding.ShardManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main server tick loop. Coordinates subsystem work while minimizing tick stalls.
 */
public final class TickLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(TickLoop.class);
    private static final long TICK_NANOS = 50_000_000L;

    private final ServerScheduler scheduler;
    private final ShardManager shardManager;
    private final ChunkManager chunkManager;
    private final EntityManager entityManager;
    private final NetworkManager networkManager;
    private final PerformanceMonitor performanceMonitor;
    private final ShardedMCConfig config;
    private final AdaptivePerformanceManager adaptiveManager;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread tickThread;
    private long currentTick;

    public TickLoop(
            ServerScheduler scheduler,
            ShardManager shardManager,
            ChunkManager chunkManager,
            EntityManager entityManager,
            NetworkManager networkManager,
            PerformanceMonitor performanceMonitor,
            ShardedMCConfig config
    ) {
        this.scheduler = scheduler;
        this.shardManager = shardManager;
        this.chunkManager = chunkManager;
        this.entityManager = entityManager;
        this.networkManager = networkManager;
        this.performanceMonitor = performanceMonitor;
        this.config = config;
        this.adaptiveManager = new AdaptivePerformanceManager(config, scheduler, performanceMonitor);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        tickThread = new Thread(this::runLoop, "ShardedMC-TickLoop");
        tickThread.setDaemon(false);
        tickThread.start();
    }

    public void stop() {
        running.set(false);
        if (tickThread != null) {
            try {
                tickThread.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runLoop() {
        long nextTick = System.nanoTime();
        while (running.get()) {
            long tickStart = System.nanoTime();
            try {
                executeTick();
            } catch (Exception e) {
                LOGGER.error("Error during tick {}", currentTick, e);
            }
            long tickDuration = System.nanoTime() - tickStart;
            performanceMonitor.recordTick(tickDuration);
            adaptiveManager.adjustIfNeeded();

            nextTick += TICK_NANOS;
            long sleepNanos = nextTick - System.nanoTime();
            if (sleepNanos > 0) {
                sleepNanos(sleepNanos);
            } else if (sleepNanos < -TICK_NANOS * 5) {
                nextTick = System.nanoTime();
            }
            currentTick++;
        }
    }

    private void executeTick() {
        networkManager.processInbound();
        chunkManager.processTick(currentTick);
        shardManager.processTick(currentTick);
        entityManager.tick(currentTick);
        networkManager.processOutbound();
        chunkManager.processUnloads(currentTick);
    }

    private static void sleepNanos(long nanos) {
        try {
            long millis = nanos / 1_000_000L;
            int remaining = (int) (nanos % 1_000_000L);
            Thread.sleep(millis, remaining);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public long getCurrentTick() {
        return currentTick;
    }

    public boolean isRunning() {
        return running.get();
    }
}
