package com.shardedmc.benchmark;

import com.shardedmc.config.ShardedMCConfig;
import com.shardedmc.diagnostics.PerformanceMonitor;
import com.shardedmc.scheduler.ServerScheduler;
import com.shardedmc.sharding.ShardManager;
import com.shardedmc.world.Chunk;
import com.shardedmc.world.ChunkPos;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark suite comparing chunk loading strategies.
 */
public final class BenchmarkRunner {

    public static void main(String[] args) throws Exception {
        System.out.println("=== ShardedMC Benchmark Suite ===\n");

        Files.createTempDirectory("shardedmc-bench");

        List<BenchmarkResult> results = new ArrayList<>();
        results.add(runBenchmark("Vanilla (Single-threaded)", new VanillaChunkLoader()));
        results.add(runBenchmark("Conventional Async", new AsyncConventionalLoader()));
        results.add(runBenchmark("ShardedMC", new ShardedMCLoader()));

        printResults(results);
    }

    private static BenchmarkResult runBenchmark(String name, ChunkLoadStrategy strategy) throws Exception {
        System.out.println("Running: " + name + "...");
        ShardedMCConfig config = ShardedMCConfig.defaults();
        ServerScheduler scheduler = new ServerScheduler(config);
        PerformanceMonitor monitor = new PerformanceMonitor(config);
        ShardManager shardManager = new ShardManager(config, scheduler);

        int chunkCount = 256;
        int warmup = 32;
        long[] tickTimes = new long[chunkCount];

        // Warmup
        for (int i = 0; i < warmup; i++) {
            strategy.loadChunk(new ChunkPos(i, 0), scheduler, shardManager, monitor);
        }

        long start = System.nanoTime();
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            long tickStart = System.nanoTime();
            futures.add(strategy.loadChunk(new ChunkPos(i + warmup, i % 16), scheduler, shardManager, monitor));
            tickTimes[i] = System.nanoTime() - tickStart;
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        long totalTime = System.nanoTime() - start;

        double avgTickMs = average(tickTimes) / 1_000_000.0;
        double p95 = percentile(tickTimes, 0.95) / 1_000_000.0;
        double p99 = percentile(tickTimes, 0.99) / 1_000_000.0;
        double tps = 1_000_000_000.0 / (totalTime / (double) chunkCount);

        scheduler.shutdown();

        return new BenchmarkResult(name, tps, avgTickMs, p95, p99,
                monitor.getAverageChunkLoadLatencyMs(),
                monitor.getAverageChunkGenLatencyMs(),
                Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
    }

    private static void printResults(List<BenchmarkResult> results) {
        System.out.println("\n=== Benchmark Results ===\n");
        System.out.printf("%-30s %8s %10s %10s %10s %12s %12s %10s%n",
                "Strategy", "TPS", "Avg(ms)", "P95(ms)", "P99(ms)", "Load(ms)", "Gen(ms)", "Memory(MB)");
        System.out.println("-".repeat(110));
        for (BenchmarkResult r : results) {
            System.out.printf("%-30s %8.2f %10.3f %10.3f %10.3f %12.3f %12.3f %10.2f%n",
                    r.name(), r.tps(), r.avgTickMs(), r.p95Ms(), r.p99Ms(),
                    r.loadLatencyMs(), r.genLatencyMs(), r.memoryBytes() / (1024.0 * 1024.0));
        }
        System.out.println("\nNote: Results are from simulated chunk loading workloads.");
        System.out.println("Run on production hardware with real world data for authoritative metrics.");
    }

    private static double average(long[] values) {
        long sum = 0;
        for (long v : values) {
            sum += v;
        }
        return sum / (double) values.length;
    }

    private static double percentile(long[] values, double p) {
        long[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int index = (int) (sorted.length * p);
        return sorted[Math.min(index, sorted.length - 1)];
    }

    record BenchmarkResult(
            String name,
            double tps,
            double avgTickMs,
            double p95Ms,
            double p99Ms,
            double loadLatencyMs,
            double genLatencyMs,
            long memoryBytes
    ) {
    }

    interface ChunkLoadStrategy {
        CompletableFuture<Chunk> loadChunk(
                ChunkPos pos,
                ServerScheduler scheduler,
                ShardManager shardManager,
                PerformanceMonitor monitor
        );
    }

    static final class VanillaChunkLoader implements ChunkLoadStrategy {
        @Override
        public CompletableFuture<Chunk> loadChunk(
                ChunkPos pos, ServerScheduler scheduler,
                ShardManager shardManager, PerformanceMonitor monitor
        ) {
            return CompletableFuture.supplyAsync(() -> {
                long start = System.nanoTime();
                Chunk chunk = new Chunk(pos);
                simulateGeneration(chunk);
                monitor.recordChunkLoadLatency(System.nanoTime() - start);
                monitor.recordTick(System.nanoTime() - start);
                return chunk;
            }, scheduler.poolFor(ServerScheduler.PoolType.MAIN));
        }
    }

    static final class AsyncConventionalLoader implements ChunkLoadStrategy {
        @Override
        public CompletableFuture<Chunk> loadChunk(
                ChunkPos pos, ServerScheduler scheduler,
                ShardManager shardManager, PerformanceMonitor monitor
        ) {
            long start = System.nanoTime();
            return scheduler.submit(ServerScheduler.PoolType.CHUNK_IO, () -> {
                Chunk chunk = new Chunk(pos);
                simulateIo();
                return chunk;
            }).thenCompose(chunk ->
                    scheduler.submit(ServerScheduler.PoolType.CHUNK_GENERATION, () -> {
                        simulateGeneration(chunk);
                        monitor.recordChunkLoadLatency(System.nanoTime() - start);
                        monitor.recordTick(System.nanoTime() - start);
                        return chunk;
                    }));
        }
    }

    static final class ShardedMCLoader implements ChunkLoadStrategy {
        @Override
        public CompletableFuture<Chunk> loadChunk(
                ChunkPos pos, ServerScheduler scheduler,
                ShardManager shardManager, PerformanceMonitor monitor
        ) {
            long start = System.nanoTime();
            shardManager.getOrCreateShard(pos);
            return scheduler.submit(ServerScheduler.PoolType.CHUNK_IO, () -> {
                        Chunk chunk = new Chunk(pos);
                        simulateIo();
                        return chunk;
                    })
                    .thenCompose(chunk -> scheduler.submit(ServerScheduler.PoolType.CHUNK_GENERATION, () -> {
                        simulateGeneration(chunk);
                        return chunk;
                    }))
                    .thenCompose(chunk -> scheduler.submit(ServerScheduler.PoolType.LIGHTING, () -> {
                        chunk.setState(Chunk.State.READY);
                        return chunk;
                    }))
                    .thenApply(chunk -> {
                        monitor.recordChunkLoadLatency(System.nanoTime() - start);
                        monitor.recordTick(System.nanoTime() - start);
                        return chunk;
                    });
        }
    }

    private static void simulateIo() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void simulateGeneration(Chunk chunk) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.setBlock(x, 64, z, (short) 1);
            }
        }
        chunk.setState(Chunk.State.READY);
    }
}
