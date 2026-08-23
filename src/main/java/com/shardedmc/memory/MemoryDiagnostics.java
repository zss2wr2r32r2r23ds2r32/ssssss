package com.shardedmc.memory;

import com.shardedmc.diagnostics.PerformanceMonitor;

/**
 * Optional memory diagnostics and reporting.
 */
public final class MemoryDiagnostics {

    private final PerformanceMonitor monitor;

    public MemoryDiagnostics(PerformanceMonitor monitor) {
        this.monitor = monitor;
    }

    public String report() {
        PerformanceMonitor.MemoryStats stats = monitor.getMemoryStats();
        Runtime runtime = Runtime.getRuntime();
        return String.format(
                "Memory Diagnostics:%n  Heap Used: %d MB%n  Heap Max: %d MB%n  Heap Free: %d MB%n  Non-Heap: %d MB%n  Usage: %.1f%%",
                stats.heapUsed() / (1024 * 1024),
                stats.heapMax() / (1024 * 1024),
                runtime.freeMemory() / (1024 * 1024),
                stats.nonHeapUsed() / (1024 * 1024),
                stats.heapUsagePercent()
        );
    }
}
