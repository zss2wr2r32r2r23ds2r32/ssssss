package com.shardedmc.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads and persists the ShardedMC performance configuration (shardedmc.yml).
 */
public final class ShardedMCConfig {

    private ShardingConfig sharding = new ShardingConfig();
    private ChunksConfig chunks = new ChunksConfig();
    private NetworkConfig network = new NetworkConfig();
    private PerformanceConfig performance = new PerformanceConfig();
    private EntityConfig entity = new EntityConfig();
    private ReliabilityConfig reliability = new ReliabilityConfig();

    @SuppressWarnings("unchecked")
    public static ShardedMCConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            ShardedMCConfig defaults = defaults();
            defaults.save(path);
            return defaults;
        }
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(path)) {
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                return defaults();
            }
            ShardedMCConfig config = defaults();
            if (root.containsKey("sharding")) {
                config.sharding = parseSharding((Map<String, Object>) root.get("sharding"));
            }
            if (root.containsKey("chunks")) {
                config.chunks = parseChunks((Map<String, Object>) root.get("chunks"));
            }
            if (root.containsKey("network")) {
                config.network = parseNetwork((Map<String, Object>) root.get("network"));
            }
            if (root.containsKey("performance")) {
                config.performance = parsePerformance((Map<String, Object>) root.get("performance"));
            }
            if (root.containsKey("entity")) {
                config.entity = parseEntity((Map<String, Object>) root.get("entity"));
            }
            if (root.containsKey("reliability")) {
                config.reliability = parseReliability((Map<String, Object>) root.get("reliability"));
            }
            return config;
        }
    }

    private static ShardingConfig parseSharding(Map<String, Object> map) {
        ShardingConfig c = new ShardingConfig();
        c.enabled = bool(map, "enabled", c.enabled);
        c.workerThreads = str(map, "worker-threads", c.workerThreads);
        c.regionSize = integer(map, "region-size", c.regionSize);
        c.lockTimeoutMs = integer(map, "lock-timeout-ms", c.lockTimeoutMs);
        return c;
    }

    private static ChunksConfig parseChunks(Map<String, Object> map) {
        ChunksConfig c = new ChunksConfig();
        c.asyncLoading = bool(map, "async-loading", c.asyncLoading);
        c.asyncGeneration = bool(map, "async-generation", c.asyncGeneration);
        c.prefetch = bool(map, "prefetch", c.prefetch);
        c.cacheSize = integer(map, "cache-size", c.cacheSize);
        c.unloadDelay = integer(map, "unload-delay", c.unloadDelay);
        c.ioThreads = integer(map, "io-threads", c.ioThreads);
        c.generationThreads = integer(map, "generation-threads", c.generationThreads);
        c.lightingThreads = integer(map, "lighting-threads", c.lightingThreads);
        c.prefetchRadius = integer(map, "prefetch-radius", c.prefetchRadius);
        return c;
    }

    private static NetworkConfig parseNetwork(Map<String, Object> map) {
        NetworkConfig c = new NetworkConfig();
        c.workerThreads = str(map, "worker-threads", c.workerThreads);
        c.compressionLevel = str(map, "compression-level", c.compressionLevel);
        c.maxPacketBatchSize = integer(map, "max-packet-batch-size", c.maxPacketBatchSize);
        c.asyncProcessing = bool(map, "async-processing", c.asyncProcessing);
        return c;
    }

    private static PerformanceConfig parsePerformance(Map<String, Object> map) {
        PerformanceConfig c = new PerformanceConfig();
        c.adaptiveThreading = bool(map, "adaptive-threading", c.adaptiveThreading);
        c.profiling = bool(map, "profiling", c.profiling);
        c.metrics = bool(map, "metrics", c.metrics);
        long slowMs = integer(map, "slow-tick-threshold-ms", (int) (c.slowTickThresholdNanos / 1_000_000L));
        c.slowTickThresholdNanos = slowMs * 1_000_000L;
        c.targetTps = integer(map, "target-tps", c.targetTps);
        return c;
    }

    private static EntityConfig parseEntity(Map<String, Object> map) {
        EntityConfig c = new EntityConfig();
        c.concurrentTicking = bool(map, "concurrent-ticking", c.concurrentTicking);
        c.inactiveTickInterval = integer(map, "inactive-tick-interval", c.inactiveTickInterval);
        c.spatialIndexCellSize = integer(map, "spatial-index-cell-size", c.spatialIndexCellSize);
        return c;
    }

    private static ReliabilityConfig parseReliability(Map<String, Object> map) {
        ReliabilityConfig c = new ReliabilityConfig();
        c.isolateWorkerFailures = bool(map, "isolate-worker-failures", c.isolateWorkerFailures);
        c.workerStallThresholdMs = integer(map, "worker-stall-threshold-ms", (int) c.workerStallThresholdMs);
        c.crashRecovery = bool(map, "crash-recovery", c.crashRecovery);
        return c;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        return value instanceof Boolean b ? b : defaultValue;
    }

    private static int integer(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    private static String str(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    public static ShardedMCConfig defaults() {
        return new ShardedMCConfig();
    }

    public void save(Path path) throws IOException {
        Yaml yaml = new Yaml();
        try (Writer writer = Files.newBufferedWriter(path)) {
            yaml.dump(asMap(), writer);
        }
    }

    private Map<String, Object> asMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("sharding", sharding.toMap());
        root.put("chunks", chunks.toMap());
        root.put("network", network.toMap());
        root.put("performance", performance.toMap());
        root.put("entity", entity.toMap());
        root.put("reliability", reliability.toMap());
        return root;
    }

    public ShardingConfig getSharding() {
        return sharding;
    }

    public void setSharding(ShardingConfig sharding) {
        this.sharding = sharding;
    }

    public ChunksConfig getChunks() {
        return chunks;
    }

    public void setChunks(ChunksConfig chunks) {
        this.chunks = chunks;
    }

    public NetworkConfig getNetwork() {
        return network;
    }

    public void setNetwork(NetworkConfig network) {
        this.network = network;
    }

    public PerformanceConfig getPerformance() {
        return performance;
    }

    public void setPerformance(PerformanceConfig performance) {
        this.performance = performance;
    }

    public EntityConfig getEntity() {
        return entity;
    }

    public void setEntity(EntityConfig entity) {
        this.entity = entity;
    }

    public ReliabilityConfig getReliability() {
        return reliability;
    }

    public void setReliability(ReliabilityConfig reliability) {
        this.reliability = reliability;
    }

    public static final class ShardingConfig {
        private boolean enabled = true;
        private String workerThreads = "auto";
        private int regionSize = 8;
        private int lockTimeoutMs = 5000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(String workerThreads) {
            this.workerThreads = workerThreads;
        }

        public int getRegionSize() {
            return regionSize;
        }

        public void setRegionSize(int regionSize) {
            this.regionSize = regionSize;
        }

        public int getLockTimeoutMs() {
            return lockTimeoutMs;
        }

        public void setLockTimeoutMs(int lockTimeoutMs) {
            this.lockTimeoutMs = lockTimeoutMs;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("enabled", enabled);
            map.put("worker-threads", workerThreads);
            map.put("region-size", regionSize);
            map.put("lock-timeout-ms", lockTimeoutMs);
            return map;
        }
    }

    public static final class ChunksConfig {
        private boolean asyncLoading = true;
        private boolean asyncGeneration = true;
        private boolean prefetch = true;
        private int cacheSize = 2048;
        private int unloadDelay = 300;
        private int ioThreads = 4;
        private int generationThreads = 4;
        private int lightingThreads = 2;
        private int prefetchRadius = 2;

        public boolean isAsyncLoading() {
            return asyncLoading;
        }

        public void setAsyncLoading(boolean asyncLoading) {
            this.asyncLoading = asyncLoading;
        }

        public boolean isAsyncGeneration() {
            return asyncGeneration;
        }

        public void setAsyncGeneration(boolean asyncGeneration) {
            this.asyncGeneration = asyncGeneration;
        }

        public boolean isPrefetch() {
            return prefetch;
        }

        public void setPrefetch(boolean prefetch) {
            this.prefetch = prefetch;
        }

        public int getCacheSize() {
            return cacheSize;
        }

        public void setCacheSize(int cacheSize) {
            this.cacheSize = cacheSize;
        }

        public int getUnloadDelay() {
            return unloadDelay;
        }

        public void setUnloadDelay(int unloadDelay) {
            this.unloadDelay = unloadDelay;
        }

        public int getIoThreads() {
            return ioThreads;
        }

        public void setIoThreads(int ioThreads) {
            this.ioThreads = ioThreads;
        }

        public int getGenerationThreads() {
            return generationThreads;
        }

        public void setGenerationThreads(int generationThreads) {
            this.generationThreads = generationThreads;
        }

        public int getLightingThreads() {
            return lightingThreads;
        }

        public void setLightingThreads(int lightingThreads) {
            this.lightingThreads = lightingThreads;
        }

        public int getPrefetchRadius() {
            return prefetchRadius;
        }

        public void setPrefetchRadius(int prefetchRadius) {
            this.prefetchRadius = prefetchRadius;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("async-loading", asyncLoading);
            map.put("async-generation", asyncGeneration);
            map.put("prefetch", prefetch);
            map.put("cache-size", cacheSize);
            map.put("unload-delay", unloadDelay);
            map.put("io-threads", ioThreads);
            map.put("generation-threads", generationThreads);
            map.put("lighting-threads", lightingThreads);
            map.put("prefetch-radius", prefetchRadius);
            return map;
        }
    }

    public static final class NetworkConfig {
        private String workerThreads = "auto";
        private String compressionLevel = "adaptive";
        private int maxPacketBatchSize = 64;
        private boolean asyncProcessing = true;

        public String getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(String workerThreads) {
            this.workerThreads = workerThreads;
        }

        public String getCompressionLevel() {
            return compressionLevel;
        }

        public void setCompressionLevel(String compressionLevel) {
            this.compressionLevel = compressionLevel;
        }

        public int getMaxPacketBatchSize() {
            return maxPacketBatchSize;
        }

        public void setMaxPacketBatchSize(int maxPacketBatchSize) {
            this.maxPacketBatchSize = maxPacketBatchSize;
        }

        public boolean isAsyncProcessing() {
            return asyncProcessing;
        }

        public void setAsyncProcessing(boolean asyncProcessing) {
            this.asyncProcessing = asyncProcessing;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("worker-threads", workerThreads);
            map.put("compression-level", compressionLevel);
            map.put("max-packet-batch-size", maxPacketBatchSize);
            map.put("async-processing", asyncProcessing);
            return map;
        }
    }

    public static final class PerformanceConfig {
        private boolean adaptiveThreading = true;
        private boolean profiling = false;
        private boolean metrics = true;
        private long slowTickThresholdNanos = 50_000_000L;
        private int targetTps = 20;

        public boolean isAdaptiveThreading() {
            return adaptiveThreading;
        }

        public void setAdaptiveThreading(boolean adaptiveThreading) {
            this.adaptiveThreading = adaptiveThreading;
        }

        public boolean isProfiling() {
            return profiling;
        }

        public void setProfiling(boolean profiling) {
            this.profiling = profiling;
        }

        public boolean isMetrics() {
            return metrics;
        }

        public void setMetrics(boolean metrics) {
            this.metrics = metrics;
        }

        public long getSlowTickThresholdNanos() {
            return slowTickThresholdNanos;
        }

        public void setSlowTickThresholdNanos(long slowTickThresholdNanos) {
            this.slowTickThresholdNanos = slowTickThresholdNanos;
        }

        public int getTargetTps() {
            return targetTps;
        }

        public void setTargetTps(int targetTps) {
            this.targetTps = targetTps;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("adaptive-threading", adaptiveThreading);
            map.put("profiling", profiling);
            map.put("metrics", metrics);
            map.put("slow-tick-threshold-ms", slowTickThresholdNanos / 1_000_000L);
            map.put("target-tps", targetTps);
            return map;
        }
    }

    public static final class EntityConfig {
        private boolean concurrentTicking = true;
        private int inactiveTickInterval = 4;
        private int spatialIndexCellSize = 16;

        public boolean isConcurrentTicking() {
            return concurrentTicking;
        }

        public void setConcurrentTicking(boolean concurrentTicking) {
            this.concurrentTicking = concurrentTicking;
        }

        public int getInactiveTickInterval() {
            return inactiveTickInterval;
        }

        public void setInactiveTickInterval(int inactiveTickInterval) {
            this.inactiveTickInterval = inactiveTickInterval;
        }

        public int getSpatialIndexCellSize() {
            return spatialIndexCellSize;
        }

        public void setSpatialIndexCellSize(int spatialIndexCellSize) {
            this.spatialIndexCellSize = spatialIndexCellSize;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("concurrent-ticking", concurrentTicking);
            map.put("inactive-tick-interval", inactiveTickInterval);
            map.put("spatial-index-cell-size", spatialIndexCellSize);
            return map;
        }
    }

    public static final class ReliabilityConfig {
        private boolean isolateWorkerFailures = true;
        private long workerStallThresholdMs = 30_000L;
        private boolean crashRecovery = true;

        public boolean isIsolateWorkerFailures() {
            return isolateWorkerFailures;
        }

        public void setIsolateWorkerFailures(boolean isolateWorkerFailures) {
            this.isolateWorkerFailures = isolateWorkerFailures;
        }

        public long getWorkerStallThresholdMs() {
            return workerStallThresholdMs;
        }

        public void setWorkerStallThresholdMs(long workerStallThresholdMs) {
            this.workerStallThresholdMs = workerStallThresholdMs;
        }

        public boolean isCrashRecovery() {
            return crashRecovery;
        }

        public void setCrashRecovery(boolean crashRecovery) {
            this.crashRecovery = crashRecovery;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("isolate-worker-failures", isolateWorkerFailures);
            map.put("worker-stall-threshold-ms", workerStallThresholdMs);
            map.put("crash-recovery", crashRecovery);
            return map;
        }
    }
}
