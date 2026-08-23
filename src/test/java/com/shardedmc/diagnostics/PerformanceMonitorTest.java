package com.shardedmc.diagnostics;

import com.shardedmc.config.ShardedMCConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceMonitorTest {

    @Test
    void recordsTickMetrics() {
        PerformanceMonitor monitor = new PerformanceMonitor(ShardedMCConfig.defaults());
        monitor.recordTick(50_000_000L);
        monitor.recordTick(60_000_000L);

        assertEquals(2, monitor.getTotalTicks());
        assertEquals(55_000_000L, monitor.getAverageTickNanos(), 1);
        assertEquals(60_000_000L, monitor.getMaxTickNanos());
    }

    @Test
    void histogramPercentiles() {
        TickHistogram histogram = new TickHistogram();
        for (int i = 0; i < 100; i++) {
            histogram.record(i * 1_000_000L);
        }
        assertTrue(histogram.getP50() > 0);
        assertTrue(histogram.getP95() >= histogram.getP50());
        assertTrue(histogram.getP99() >= histogram.getP95());
    }
}
