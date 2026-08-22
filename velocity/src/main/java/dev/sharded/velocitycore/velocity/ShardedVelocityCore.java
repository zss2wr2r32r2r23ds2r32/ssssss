package dev.sharded.velocitycore.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.sharded.velocitycore.velocity.command.QueueCommand;
import dev.sharded.velocitycore.velocity.listener.PlayerDisconnectListener;
import dev.sharded.velocitycore.velocity.config.PluginConfig;
import dev.sharded.velocitycore.velocity.queue.QueueManager;
import dev.sharded.velocitycore.velocity.status.ServerStatusManager;
import dev.sharded.velocitycore.velocity.status.StatusSyncService;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "shardedvelocitycore",
        name = "ShardedVelocityCore",
        version = "1.0.0",
        description = "Server status placeholders, queue system, and hologram status sync for Velocity networks.",
        authors = {"Sharded"}
)
public final class ShardedVelocityCore {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private PluginConfig config;
    private ServerStatusManager statusManager;
    private QueueManager queueManager;
    private StatusSyncService statusSyncService;

    @Inject
    public ShardedVelocityCore(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.config = PluginConfig.load(dataDirectory, logger);
        this.statusManager = new ServerStatusManager(server, config);
        this.queueManager = new QueueManager(server, logger, config, statusManager);
        this.statusSyncService = new StatusSyncService(server, statusManager);

        statusManager.start();
        queueManager.start();
        statusSyncService.start();

        server.getEventManager().register(this, new PlayerDisconnectListener(queueManager));

        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("queue").build(),
                new QueueCommand(queueManager, config)
        );

        logger.info("ShardedVelocityCore enabled. Placeholders: %shardedvelocitycore_status_<server>%");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (statusSyncService != null) {
            statusSyncService.stop();
        }
        if (queueManager != null) {
            queueManager.stop();
        }
        if (statusManager != null) {
            statusManager.stop();
        }
    }

    public ServerStatusManager statusManager() {
        return statusManager;
    }

    public QueueManager queueManager() {
        return queueManager;
    }

    public PluginConfig config() {
        return config;
    }
}
