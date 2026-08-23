package com.shardedmc.diagnostics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Histogram for tick duration percentiles (p50, p95, p99).
 */
public final class TickHistogram {

    private static final int BUCKET_COUNT = 100;
    private static final long BUCKET_SIZE_NANOS = 1_000_000L; // 1ms per bucket

    private final LongAdder[] buckets = new LongAdder[BUCKET_COUNT];
    private final LongAdder totalSamples = new LongAdder();
    private final AtomicLong maxValue = new AtomicLong();

    public TickHistogram() {
        for (int i = 0; i < BUCKET_COUNT; i++) {
            buckets[i] = new LongAdder();
        }
    }

    public void record(long nanos) {
        int bucket = (int) Math.min(nanos / BUCKET_SIZE_NANOS, BUCKET_COUNT - 1);
        buckets[bucket].increment();
        totalSamples.increment();
        maxValue.updateAndGet(current -> Math.max(current, nanos));
    }

    public long getPercentile(double percentile) {
        long target = (long) (totalSamples.sum() * percentile);
        long cumulative = 0;
        for (int i = 0; i < BUCKET_COUNT; i++) {
            cumulative += buckets[i].sum();
            if (cumulative >= target) {
                return (i + 1) * BUCKET_SIZE_NANOS;
            }
        }
        return maxValue.get();
    }

    public long getP50() {
        return getPercentile(0.50);
    }

    public long getP95() {
        return getPercentile(0.95);
    }

    public long getP99() {
        return getPercentile(0.99);
    }
}
