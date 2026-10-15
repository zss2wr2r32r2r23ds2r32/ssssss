package com.shardedmc.diagnostics;

import com.shardedmc.config.ShardedMCConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Built-in profiling tools for identifying performance bottlenecks.
 */
public final class Profiler {

    private static final Logger LOGGER = LoggerFactory.getLogger(Profiler.class);

    private final ShardedMCConfig config;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final Map<String, ProfileSection> sections = new ConcurrentHashMap<>();

    public Profiler(ShardedMCConfig config) {
        this.config = config;
        if (config.getPerformance().isProfiling()) {
            active.set(true);
        }
    }

    public void start() {
        active.set(true);
        sections.clear();
        LOGGER.info("Profiler started");
    }

    public void stop() {
        active.set(false);
        LOGGER.info("Profiler stopped");
    }

    public boolean isActive() {
        return active.get() || config.getPerformance().isProfiling();
    }

    public ProfileSection section(String name) {
        return sections.computeIfAbsent(name, ProfileSection::new);
    }

    public void time(String name, Runnable task) {
        if (!isActive()) {
            task.run();
            return;
        }
        ProfileSection section = section(name);
        long start = System.nanoTime();
        try {
            task.run();
        } finally {
            section.record(System.nanoTime() - start);
        }
    }

    public Map<String, ProfileSection> getSections() {
        return Map.copyOf(sections);
    }

    public static final class ProfileSection {
        private final String name;
        private long totalNanos;
        private long callCount;
        private long maxNanos;

        ProfileSection(String name) {
            this.name = name;
        }

        synchronized void record(long nanos) {
            totalNanos += nanos;
            callCount++;
            if (nanos > maxNanos) {
                maxNanos = nanos;
            }
        }

        public String getName() {
            return name;
        }

        public synchronized double getAverageMs() {
            return callCount == 0 ? 0 : (totalNanos / (double) callCount) / 1_000_000.0;
        }

        public synchronized long getCallCount() {
            return callCount;
        }

        public synchronized double getMaxMs() {
            return maxNanos / 1_000_000.0;
        }

        public synchronized double getTotalMs() {
            return totalNanos / 1_000_000.0;
        }
    }
}
