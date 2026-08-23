package com.shardedmc.scheduler;

import com.shardedmc.config.ShardedMCConfig;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central scheduler providing separate execution domains for each subsystem.
 * Operations that must remain synchronized are submitted to the main thread queue.
 */
public final class ServerScheduler {

    public enum PoolType {
        MAIN,
        SHARD,
        CHUNK_IO,
        CHUNK_GENERATION,
        LIGHTING,
        ENTITY,
        NETWORK,
        PLUGIN,
        WORLD_TASK
    }

    private final ExecutorService mainExecutor;
    private final ThreadPoolExecutor shardPool;
    private final ThreadPoolExecutor chunkIoPool;
    private final ThreadPoolExecutor generationPool;
    private final ThreadPoolExecutor lightingPool;
    private final ThreadPoolExecutor entityPool;
    private final ThreadPoolExecutor networkPool;
    private final ThreadPoolExecutor pluginPool;
    private final ThreadPoolExecutor worldTaskPool;

    private final BlockingQueue<Runnable> mainQueue = new LinkedBlockingQueue<>();
    private final AtomicLong mainTaskCounter = new AtomicLong();

    public ServerScheduler(ShardedMCConfig config) {
        int cores = Runtime.getRuntime().availableProcessors();
        int shardThreads = resolveThreadCount(config.getSharding().getWorkerThreads(), cores);
        int networkThreads = resolveThreadCount(config.getNetwork().getWorkerThreads(), Math.max(2, cores / 2));

        this.mainExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ShardedMC-Main");
            t.setDaemon(false);
            return t;
        });

        this.shardPool = createPool("Shard", shardThreads, shardThreads * 2);
        this.chunkIoPool = createPool("ChunkIO", config.getChunks().getIoThreads(), config.getChunks().getIoThreads());
        this.generationPool = createPool("ChunkGen", config.getChunks().getGenerationThreads(),
                config.getChunks().getGenerationThreads() * 2);
        this.lightingPool = createPool("Lighting", config.getChunks().getLightingThreads(),
                config.getChunks().getLightingThreads());
        this.entityPool = createPool("Entity", Math.max(2, cores / 2), cores);
        this.networkPool = createPool("Network", networkThreads, networkThreads * 2);
        this.pluginPool = createPool("Plugin", 2, 4);
        this.worldTaskPool = createPool("WorldTask", 2, cores);
    }

    private static int resolveThreadCount(String setting, int defaultCount) {
        if ("auto".equalsIgnoreCase(setting)) {
            return Math.max(2, defaultCount);
        }
        return Integer.parseInt(setting);
    }

    private static ThreadPoolExecutor createPool(String name, int core, int max) {
        return new ThreadPoolExecutor(
                core, max, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10_000),
                r -> {
                    Thread t = new Thread(r, "ShardedMC-" + name);
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * Runs a task on the main synchronized thread (required for world mutations).
     */
    public CompletableFuture<Void> runMain(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        mainExecutor.execute(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public void runMainSync(Runnable task) {
        if (Thread.currentThread().getName().equals("ShardedMC-Main")) {
            task.run();
            return;
        }
        runMain(task).join();
    }

    public <T> CompletableFuture<T> submit(PoolType type, Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, poolFor(type));
    }

    public CompletableFuture<Void> submit(PoolType type, Runnable task) {
        return submit(type, Executors.callable(task, null));
    }

    public ExecutorService poolFor(PoolType type) {
        return switch (type) {
            case MAIN -> mainExecutor;
            case SHARD -> shardPool;
            case CHUNK_IO -> chunkIoPool;
            case CHUNK_GENERATION -> generationPool;
            case LIGHTING -> lightingPool;
            case ENTITY -> entityPool;
            case NETWORK -> networkPool;
            case PLUGIN -> pluginPool;
            case WORLD_TASK -> worldTaskPool;
        };
    }

    public int getQueueDepth(PoolType type) {
        ExecutorService pool = poolFor(type);
        if (pool instanceof ThreadPoolExecutor tpe) {
            return tpe.getQueue().size();
        }
        return 0;
    }

    public int getActiveCount(PoolType type) {
        ExecutorService pool = poolFor(type);
        if (pool instanceof ThreadPoolExecutor tpe) {
            return tpe.getActiveCount();
        }
        return 0;
    }

    public long getMainTaskCount() {
        return mainTaskCounter.get();
    }

    public void shutdown() {
        shutdownPool(mainExecutor);
        shutdownPool(shardPool);
        shutdownPool(chunkIoPool);
        shutdownPool(generationPool);
        shutdownPool(lightingPool);
        shutdownPool(entityPool);
        shutdownPool(networkPool);
        shutdownPool(pluginPool);
        shutdownPool(worldTaskPool);
    }

    private static void shutdownPool(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
