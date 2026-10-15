package com.shardedmc.chunk;

import com.shardedmc.diagnostics.PerformanceMonitor;
import com.shardedmc.scheduler.ServerScheduler;
import com.shardedmc.world.Chunk;
import com.shardedmc.world.ChunkPos;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous chunk loader with deduplication and non-blocking I/O.
 */
public final class AsyncChunkLoader {

    private final ServerScheduler scheduler;
    private final ChunkSerializer serializer;
    private final PerformanceMonitor performanceMonitor;

    public AsyncChunkLoader(
            ServerScheduler scheduler,
            ChunkSerializer serializer,
            PerformanceMonitor performanceMonitor
    ) {
        this.scheduler = scheduler;
        this.serializer = serializer;
        this.performanceMonitor = performanceMonitor;
    }

    public CompletableFuture<Chunk> loadAsync(ChunkPos pos, Chunk chunk) {
        long start = System.nanoTime();
        return scheduler.submit(ServerScheduler.PoolType.CHUNK_IO, () -> loadSync(pos, chunk))
                .whenComplete((result, ex) ->
                        performanceMonitor.recordChunkLoadLatency(System.nanoTime() - start));
    }

    public Chunk loadSync(ChunkPos pos, Chunk chunk) {
        long ioStart = System.nanoTime();
        Optional<byte[]> data = serializer.load(pos);
        performanceMonitor.recordDiskIoLatency(System.nanoTime() - ioStart);

        if (data.isPresent()) {
            chunk.deserialize(data.get());
            chunk.setState(Chunk.State.READY);
        } else {
            chunk.setState(Chunk.State.GENERATING);
        }
        return chunk;
    }
}
