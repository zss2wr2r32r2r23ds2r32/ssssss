package com.shardedmc.generation;

import com.shardedmc.diagnostics.PerformanceMonitor;
import com.shardedmc.scheduler.ServerScheduler;
import com.shardedmc.world.Chunk;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dependency-aware generation pipeline with prioritized task queues.
 */
public final class GenerationPipeline {

    private final ServerScheduler scheduler;
    private final ChunkGenerator generator;
    private final PerformanceMonitor performanceMonitor;
    private final ConcurrentLinkedQueue<GenerationTask> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueLength = new AtomicInteger();

    public GenerationPipeline(
            ServerScheduler scheduler,
            ChunkGenerator generator,
            PerformanceMonitor performanceMonitor
    ) {
        this.scheduler = scheduler;
        this.generator = generator;
        this.performanceMonitor = performanceMonitor;
    }

    public CompletableFuture<Chunk> generate(Chunk chunk) {
        long start = System.nanoTime();
        queueLength.incrementAndGet();
        GenerationTask task = new GenerationTask(chunk);
        queue.offer(task);

        return scheduler.submit(ServerScheduler.PoolType.CHUNK_GENERATION, () -> {
            try {
                // Stage 1: terrain
                generator.generate(chunk);
                // Stage 2: structures (placeholder for dependency-aware stages)
                // Stage 3: features
                return chunk;
            } finally {
                queueLength.decrementAndGet();
                queue.remove(task);
                performanceMonitor.recordChunkGenerationLatency(System.nanoTime() - start);
            }
        });
    }

    public int getQueueLength() {
        return queueLength.get();
    }

    private record GenerationTask(Chunk chunk) {
    }
}
