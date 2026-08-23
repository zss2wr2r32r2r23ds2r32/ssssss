package com.shardedmc.reliability;

import com.shardedmc.scheduler.ServerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Detects dead or stalled worker threads and isolates task failures.
 */
public final class WorkerHealthMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerHealthMonitor.class);

    private final ServerScheduler scheduler;
    private final ScheduledExecutorService monitorExecutor;
    private final Map<String, Long> lastHeartbeat = new ConcurrentHashMap<>();
    private volatile boolean running;

    public WorkerHealthMonitor(ServerScheduler scheduler) {
        this.scheduler = scheduler;
        this.monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ShardedMC-WorkerHealth");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        running = true;
        monitorExecutor.scheduleAtFixedRate(this::checkWorkers, 5, 5, TimeUnit.SECONDS);
    }

    public void stop() {
        running = false;
        monitorExecutor.shutdown();
    }

    public void heartbeat(String workerName) {
        lastHeartbeat.put(workerName, System.currentTimeMillis());
    }

    private void checkWorkers() {
        if (!running) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ServerScheduler.PoolType type : ServerScheduler.PoolType.values()) {
            String name = type.name();
            int queueDepth = scheduler.getQueueDepth(type);
            int active = scheduler.getActiveCount(type);
            if (queueDepth > 1000 && active == 0) {
                LOGGER.warn("Potential stalled worker pool: {} (queue={}, active={})", name, queueDepth, active);
            }
            Long last = lastHeartbeat.get(name);
            if (last != null && now - last > 30_000) {
                LOGGER.warn("Worker pool {} has not reported heartbeat for {}ms", name, now - last);
            }
        }
    }

    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        for (ServerScheduler.PoolType type : ServerScheduler.PoolType.values()) {
            sb.append(type.name())
                    .append("[active=").append(scheduler.getActiveCount(type))
                    .append(",queue=").append(scheduler.getQueueDepth(type))
                    .append("] ");
        }
        return sb.toString().trim();
    }

    /**
     * Wraps a task to prevent failures from propagating to the caller.
     */
    public static Runnable isolate(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.error("Isolated worker task failure", e);
            }
        };
    }
}
