package dev.sharded.velocitycore;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.sharded.velocitycore.command.QueueCommand;
import dev.sharded.velocitycore.config.PluginConfig;
import dev.sharded.velocitycore.listener.PlayerDisconnectListener;
import dev.sharded.velocitycore.placeholder.PlaceholderHook;
import dev.sharded.velocitycore.queue.QueueManager;
import dev.sharded.velocitycore.status.ServerStatusManager;
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
        this.queueManager = new QueueManager(server, config, statusManager);

        statusManager.start();
        queueManager.start();

        server.getEventManager().register(this, new PlayerDisconnectListener(queueManager));
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("queue").build(),
                new QueueCommand(queueManager, config)
        );

        PlaceholderHook.register(this);

        logger.info("ShardedVelocityCore enabled.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (queueManager != null) {
            queueManager.stop();
        }
        if (statusManager != null) {
            statusManager.stop();
        }
    }

    public ProxyServer server() {
        return server;
    }

    public Logger logger() {
        return logger;
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
