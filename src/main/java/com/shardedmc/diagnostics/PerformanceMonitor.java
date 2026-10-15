package com.shardedmc.diagnostics;

import com.shardedmc.config.ShardedMCConfig;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects server performance metrics for adaptive tuning and diagnostics.
 */
public final class PerformanceMonitor {

    private final ShardedMCConfig config;
    private final LongAdder tickCount = new LongAdder();
    private final LongAdder totalTickNanos = new LongAdder();
    private final LongAdder slowTickCount = new LongAdder();
    private final LongAdder chunkLoadLatency = new LongAdder();
    private final LongAdder chunkLoadSamples = new LongAdder();
    private final LongAdder chunkGenLatency = new LongAdder();
    private final LongAdder chunkGenSamples = new LongAdder();
    private final LongAdder diskIoLatency = new LongAdder();
    private final LongAdder diskIoSamples = new LongAdder();
    private final LongAdder entityTickCost = new LongAdder();
    private final LongAdder entityTickSamples = new LongAdder();
    private final AtomicLong networkLoad = new AtomicLong();
    private final TickHistogram histogram = new TickHistogram();

    private volatile long maxTickNanos;
    private volatile long lastTickNanos;

    public PerformanceMonitor(ShardedMCConfig config) {
        this.config = config;
    }

    public void recordTick(long nanos) {
        tickCount.increment();
        totalTickNanos.add(nanos);
        lastTickNanos = nanos;
        histogram.record(nanos);
        if (nanos > maxTickNanos) {
            maxTickNanos = nanos;
        }
        if (config.getPerformance().isMetrics()
                && nanos > config.getPerformance().getSlowTickThresholdNanos()) {
            slowTickCount.increment();
        }
    }

    public void recordChunkLoadLatency(long nanos) {
        chunkLoadLatency.add(nanos);
        chunkLoadSamples.increment();
    }

    public void recordChunkGenerationLatency(long nanos) {
        chunkGenLatency.add(nanos);
        chunkGenSamples.increment();
    }

    public void recordDiskIoLatency(long nanos) {
        diskIoLatency.add(nanos);
        diskIoSamples.increment();
    }

    public void recordEntityTickCost(long nanos) {
        entityTickCost.add(nanos);
        entityTickSamples.increment();
    }

    public void recordNetworkLoad(long packets) {
        networkLoad.addAndGet(packets);
    }

    public long getTotalTicks() {
        return tickCount.sum();
    }

    public double getAverageTickNanos() {
        long count = tickCount.sum();
        return count == 0 ? 0 : (double) totalTickNanos.sum() / count;
    }

    public double getTps() {
        double avg = getAverageTickNanos();
        return avg == 0 ? 20.0 : 1_000_000_000.0 / avg;
    }

    public long getMaxTickNanos() {
        return maxTickNanos;
    }

    public long getLastTickNanos() {
        return lastTickNanos;
    }

    public long getSlowTickCount() {
        return slowTickCount.sum();
    }

    public double getAverageChunkLoadLatencyMs() {
        return averageMs(chunkLoadLatency, chunkLoadSamples);
    }

    public double getAverageChunkGenLatencyMs() {
        return averageMs(chunkGenLatency, chunkGenSamples);
    }

    public double getAverageDiskIoLatencyMs() {
        return averageMs(diskIoLatency, diskIoSamples);
    }

    public double getAverageEntityTickCostMs() {
        return averageMs(entityTickCost, entityTickSamples);
    }

    public long getNetworkLoad() {
        return networkLoad.get();
    }

    public TickHistogram getHistogram() {
        return histogram;
    }

    public double getCpuUsage() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            return sunOs.getProcessCpuLoad() * 100.0;
        }
        return -1;
    }

    public MemoryStats getMemoryStats() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        return new MemoryStats(
                memory.getHeapMemoryUsage().getUsed(),
                memory.getHeapMemoryUsage().getMax(),
                memory.getNonHeapMemoryUsage().getUsed()
        );
    }

    private static double averageMs(LongAdder total, LongAdder samples) {
        long s = samples.sum();
        return s == 0 ? 0 : (total.sum() / (double) s) / 1_000_000.0;
    }

    public record MemoryStats(long heapUsed, long heapMax, long nonHeapUsed) {
        public double heapUsagePercent() {
            return heapMax == 0 ? 0 : (heapUsed * 100.0) / heapMax;
        }
    }
}
