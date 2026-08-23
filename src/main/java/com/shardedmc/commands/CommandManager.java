package com.shardedmc.commands;

import com.shardedmc.chunk.ChunkManager;
import com.shardedmc.diagnostics.PerformanceMonitor;
import com.shardedmc.diagnostics.Profiler;
import com.shardedmc.entity.EntityManager;
import com.shardedmc.network.NetworkManager;
import com.shardedmc.reliability.WorkerHealthMonitor;
import com.shardedmc.scheduler.ServerScheduler;
import com.shardedmc.sharding.RegionShard;
import com.shardedmc.sharding.ShardManager;
import com.shardedmc.world.RegionPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Command handler for /shardedmc diagnostics and management commands.
 */
public final class CommandManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);

    private final ShardManager shardManager;
    private final ChunkManager chunkManager;
    private final EntityManager entityManager;
    private final NetworkManager networkManager;
    private final PerformanceMonitor performanceMonitor;
    private final Profiler profiler;
    private final ServerScheduler scheduler;
    private final WorkerHealthMonitor workerHealthMonitor;
    private final Map<String, CommandHandler> commands = new HashMap<>();

    public CommandManager(
            ShardManager shardManager,
            ChunkManager chunkManager,
            EntityManager entityManager,
            NetworkManager networkManager,
            PerformanceMonitor performanceMonitor,
            Profiler profiler,
            ServerScheduler scheduler,
            WorkerHealthMonitor workerHealthMonitor
    ) {
        this.shardManager = shardManager;
        this.chunkManager = chunkManager;
        this.entityManager = entityManager;
        this.networkManager = networkManager;
        this.performanceMonitor = performanceMonitor;
        this.profiler = profiler;
        this.scheduler = scheduler;
        this.workerHealthMonitor = workerHealthMonitor;

        register("status", this::handleStatus);
        register("performance", this::handlePerformance);
        register("shards", this::handleShards);
        register("chunks", this::handleChunks);
        register("threads", this::handleThreads);
        register("profile", this::handleProfile);
    }

    private void register(String name, CommandHandler handler) {
        commands.put(name, handler);
    }

    public String execute(String input) {
        String[] parts = input.trim().split("\\s+");
        if (parts.length == 0 || !parts[0].equalsIgnoreCase("/shardedmc")) {
            return "Unknown command";
        }
        if (parts.length < 2) {
            return "Usage: /shardedmc <status|performance|shards|chunks|threads|profile> [start|stop]";
        }

        CommandHandler handler = commands.get(parts[1].toLowerCase());
        if (handler == null) {
            return "Unknown subcommand: " + parts[1];
        }
        return handler.handle(parts);
    }

    private String handleStatus(String[] args) {
        return String.format(
                "ShardedMC Status:%n  TPS: %.2f%n  Tick: %.2fms (max: %.2fms)%n  Players: %d%n  Entities: %d%n  Shards: %d%n  Cached Chunks: %d",
                performanceMonitor.getTps(),
                performanceMonitor.getAverageTickNanos() / 1_000_000.0,
                performanceMonitor.getMaxTickNanos() / 1_000_000.0,
                networkManager.getConnectedPlayers(),
                entityManager.getEntityCount(),
                shardManager.getShardCount(),
                chunkManager.getCache().size()
        );
    }

    private String handlePerformance(String[] args) {
        PerformanceMonitor.MemoryStats mem = performanceMonitor.getMemoryStats();
        return String.format(
                "Performance Metrics:%n  Avg Tick: %.2fms%n  P95: %.2fms%n  P99: %.2fms%n  Slow Ticks: %d%n  Chunk Load: %.2fms%n  Chunk Gen: %.2fms%n  Disk I/O: %.2fms%n  Entity Tick: %.2fms%n  CPU: %.1f%%%n  Memory: %.1f%% (%d MB / %d MB)",
                performanceMonitor.getAverageTickNanos() / 1_000_000.0,
                performanceMonitor.getHistogram().getP95() / 1_000_000.0,
                performanceMonitor.getHistogram().getP99() / 1_000_000.0,
                performanceMonitor.getSlowTickCount(),
                performanceMonitor.getAverageChunkLoadLatencyMs(),
                performanceMonitor.getAverageChunkGenLatencyMs(),
                performanceMonitor.getAverageDiskIoLatencyMs(),
                performanceMonitor.getAverageEntityTickCostMs(),
                performanceMonitor.getCpuUsage(),
                mem.heapUsagePercent(),
                mem.heapUsed() / (1024 * 1024),
                mem.heapMax() / (1024 * 1024)
        );
    }

    private String handleShards(String[] args) {
        StringBuilder sb = new StringBuilder("Active Shards:\n");
        for (Map.Entry<RegionPos, RegionShard> entry : shardManager.getShards().entrySet()) {
            RegionShard shard = entry.getValue();
            sb.append(String.format("  Region %s: %d chunks, %d pending, last tick cost: %.3fms%n",
                    entry.getKey().key(),
                    shard.getLoadedChunkCount(),
                    shard.getPendingTaskCount(),
                    shard.getTickCostNanos() / 1_000_000.0));
        }
        return sb.toString();
    }

    private String handleChunks(String[] args) {
        return String.format(
                "Chunk System:%n  Cached: %d%n  In-flight loads: %d%n  Load queue: %d%n  Cache memory: %.2f MB",
                chunkManager.getCache().size(),
                chunkManager.getInFlightLoadCount(),
                chunkManager.getPlayerScheduler().getQueueSize(),
                chunkManager.getCache().estimateMemoryBytes() / (1024.0 * 1024.0)
        );
    }

    private String handleThreads(String[] args) {
        StringBuilder sb = new StringBuilder("Thread Pools:\n");
        for (ServerScheduler.PoolType type : ServerScheduler.PoolType.values()) {
            sb.append(String.format("  %s: active=%d, queue=%d%n",
                    type.name(),
                    scheduler.getActiveCount(type),
                    scheduler.getQueueDepth(type)));
        }
        sb.append(String.format("  Worker health: %s%n", workerHealthMonitor.getStatus()));
        return sb.toString();
    }

    private String handleProfile(String[] args) {
        if (args.length >= 3) {
            if ("start".equalsIgnoreCase(args[2])) {
                profiler.start();
                return "Profiler started";
            } else if ("stop".equalsIgnoreCase(args[2])) {
                profiler.stop();
            }
        }
        StringBuilder sb = new StringBuilder("Profiler Results:\n");
        for (Profiler.ProfileSection section : profiler.getSections().values()) {
            sb.append(String.format("  %s: calls=%d, avg=%.3fms, max=%.3fms, total=%.3fms%n",
                    section.getName(), section.getCallCount(),
                    section.getAverageMs(), section.getMaxMs(), section.getTotalMs()));
        }
        return sb.toString();
    }

    @FunctionalInterface
    private interface CommandHandler {
        String handle(String[] args);
    }
}
