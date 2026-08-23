package com.shardedmc.scheduler;

import com.shardedmc.config.ShardedMCConfig;

/**
 * Monitors server metrics and dynamically adjusts worker utilization and priorities.
 */
public final class AdaptivePerformanceManager {

    private final ShardedMCConfig config;
    private final ServerScheduler scheduler;
    private final com.shardedmc.diagnostics.PerformanceMonitor monitor;

    private int priorityBoost = 0;
    private long lastAdjustmentTick;

    public AdaptivePerformanceManager(
            ShardedMCConfig config,
            ServerScheduler scheduler,
            com.shardedmc.diagnostics.PerformanceMonitor monitor
    ) {
        this.config = config;
        this.scheduler = scheduler;
        this.monitor = monitor;
    }

    public void adjustIfNeeded() {
        if (!config.getPerformance().isAdaptiveThreading()) {
            return;
        }

        long tick = monitor.getTotalTicks();
        if (tick - lastAdjustmentTick < 100) {
            return;
        }
        lastAdjustmentTick = tick;

        double avgTickMs = monitor.getAverageTickNanos() / 1_000_000.0;
        int genQueue = scheduler.getQueueDepth(ServerScheduler.PoolType.CHUNK_GENERATION);
        int ioQueue = scheduler.getQueueDepth(ServerScheduler.PoolType.CHUNK_IO);

        if (avgTickMs > 55.0 || genQueue > 500 || ioQueue > 500) {
            priorityBoost = Math.min(priorityBoost + 1, 5);
        } else if (avgTickMs < 45.0 && genQueue < 50 && ioQueue < 50) {
            priorityBoost = Math.max(priorityBoost - 1, 0);
        }
    }

    public int getPriorityBoost() {
        return priorityBoost;
    }
}
