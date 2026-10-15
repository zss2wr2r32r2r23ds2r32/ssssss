package com.shardedmc.bootstrap;

import com.shardedmc.commands.CommandManager;
import com.shardedmc.config.ServerProperties;
import com.shardedmc.config.ShardedMCConfig;
import com.shardedmc.diagnostics.PerformanceMonitor;
import com.shardedmc.diagnostics.Profiler;
import com.shardedmc.entity.EntityManager;
import com.shardedmc.network.NetworkManager;
import com.shardedmc.reliability.ShutdownHandler;
import com.shardedmc.reliability.WorkerHealthMonitor;
import com.shardedmc.scheduler.ServerScheduler;
import com.shardedmc.scheduler.TickLoop;
import com.shardedmc.sharding.ShardManager;
import com.shardedmc.chunk.ChunkManager;
import com.shardedmc.api.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates server startup, subsystem wiring, and graceful shutdown.
 */
public final class ServerBootstrap {

    public static final String VERSION = "1.0.0";

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerBootstrap.class);

    private final Path serverRoot;
    private final ServerProperties properties;
    private final ShardedMCConfig config;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    private ServerScheduler scheduler;
    private ShardManager shardManager;
    private ChunkManager chunkManager;
    private EntityManager entityManager;
    private NetworkManager networkManager;
    private PerformanceMonitor performanceMonitor;
    private Profiler profiler;
    private WorkerHealthMonitor workerHealthMonitor;
    private PluginManager pluginManager;
    private CommandManager commandManager;
    private TickLoop tickLoop;

    public ServerBootstrap(Path serverRoot, ServerProperties properties, ShardedMCConfig config) {
        this.serverRoot = serverRoot;
        this.properties = properties;
        this.config = config;
    }

    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Server is already running");
        }

        LOGGER.info("Initializing ShardedMC subsystems...");
        LOGGER.info("Motd: {}", properties.getMotd());
        LOGGER.info("Port: {}, Max players: {}", properties.getServerPort(), properties.getMaxPlayers());
        LOGGER.info("Sharding enabled: {}, region size: {}",
                config.getSharding().isEnabled(), config.getSharding().getRegionSize());

        scheduler = new ServerScheduler(config);
        performanceMonitor = new PerformanceMonitor(config);
        profiler = new Profiler(config);
        shardManager = new ShardManager(config, scheduler);
        chunkManager = new ChunkManager(serverRoot, config, scheduler, shardManager, performanceMonitor);
        entityManager = new EntityManager(config, scheduler, shardManager, performanceMonitor);
        networkManager = new NetworkManager(config, scheduler, performanceMonitor);
        workerHealthMonitor = new WorkerHealthMonitor(scheduler);
        pluginManager = new PluginManager(serverRoot.resolve("plugins"));
        commandManager = new CommandManager(
                shardManager, chunkManager, entityManager, networkManager,
                performanceMonitor, profiler, scheduler, workerHealthMonitor
        );

        workerHealthMonitor.start();
        networkManager.start(properties.getServerPort(), properties.getMaxPlayers());
        chunkManager.start();
        entityManager.start();
        pluginManager.loadPlugins();

        tickLoop = new TickLoop(
                scheduler,
                shardManager,
                chunkManager,
                entityManager,
                networkManager,
                performanceMonitor,
                config
        );
        tickLoop.start();

        ShutdownHandler.registerHook(this::shutdown);
        LOGGER.info("ShardedMC is ready. TPS target: 20");
    }

    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        LOGGER.info("Initiating graceful shutdown...");
        try {
            if (tickLoop != null) {
                tickLoop.stop();
            }
            if (pluginManager != null) {
                pluginManager.disableAll();
            }
            if (chunkManager != null) {
                chunkManager.flushAndShutdown();
            }
            if (entityManager != null) {
                entityManager.shutdown();
            }
            if (networkManager != null) {
                networkManager.shutdown();
            }
            if (workerHealthMonitor != null) {
                workerHealthMonitor.stop();
            }
            if (scheduler != null) {
                scheduler.shutdown();
            }
            LOGGER.info("Shutdown complete. World data flushed safely.");
        } catch (Exception e) {
            LOGGER.error("Error during shutdown", e);
        } finally {
            shutdownLatch.countDown();
        }
    }

    public ServerProperties getProperties() {
        return properties;
    }

    public ShardedMCConfig getConfig() {
        return config;
    }

    public Path getServerRoot() {
        return serverRoot;
    }
}
