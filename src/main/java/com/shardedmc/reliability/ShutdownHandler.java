package com.shardedmc.reliability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Installs JVM shutdown hooks for graceful server termination.
 */
public final class ShutdownHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownHandler.class);
    private static final AtomicReference<Runnable> shutdownTask = new AtomicReference<>();

    private ShutdownHandler() {
    }

    public static void install() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Runnable task = shutdownTask.get();
            if (task != null) {
                LOGGER.info("Shutdown hook triggered");
                task.run();
            }
        }, "ShardedMC-ShutdownHook"));
    }

    public static void registerHook(Runnable task) {
        shutdownTask.set(task);
    }
}
