package dev.sharded.velocitycore;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.sharded.velocitycore.command.LeaveCommand;
import dev.sharded.velocitycore.command.QueueCommand;
import dev.sharded.velocitycore.command.ServerCommand;
import dev.sharded.velocitycore.common.PluginChannels;
import dev.sharded.velocitycore.config.PluginConfig;
import dev.sharded.velocitycore.listener.PlayerListener;
import dev.sharded.velocitycore.listener.WhitelistListener;
import dev.sharded.velocitycore.listener.WhitelistReportListener;
import dev.sharded.velocitycore.listener.ServerCommandListener;
import dev.sharded.velocitycore.placeholder.PlaceholderHook;
import dev.sharded.velocitycore.queue.QueueManager;
import dev.sharded.velocitycore.queue.ServerConnectService;
import dev.sharded.velocitycore.status.ServerStatusManager;
import dev.sharded.velocitycore.status.StatusSyncService;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "shardedvelocitycore",
        name = "ShardedVelocityCore",
        version = "1.0.6",
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
    private ServerConnectService connectService;

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
        this.statusSyncService = new StatusSyncService(server, statusManager, config);
        this.connectService = new ServerConnectService(server, queueManager, config);

        statusManager.setChangeListener(statusSyncService::broadcastNow);

        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("queue").aliases("q").plugin(this).build(),
                new QueueCommand(connectService)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("leave").plugin(this).build(),
                new LeaveCommand(connectService)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("server").plugin(this).build(),
                new ServerCommand(connectService)
        );

        server.getChannelRegistrar().register(
                MinecraftChannelIdentifier.from(PluginChannels.WHITELIST_CHANNEL)
        );

        server.getEventManager().register(this, new ServerCommandListener(connectService));
        server.getEventManager().register(this, new PlayerListener(queueManager, statusSyncService));
        server.getEventManager().register(this, new WhitelistListener(statusManager, statusSyncService));
        server.getEventManager().register(this, new WhitelistReportListener(statusManager, statusSyncService));

        statusManager.start();
        queueManager.start(this);
        statusSyncService.start(this);

        schedulePlaceholderRegistration();

        logger.info("ShardedVelocityCore enabled. Use /queue or /server <name> to join servers.");
        if (!PlaceholderHook.isMiniPlaceholdersLoaded(this)) {
            logger.warn("MiniPlaceholders was not found on Velocity. Install it for hologram placeholders.");
        }
    }

    private void schedulePlaceholderRegistration() {
        PlaceholderHook.register(this);

        server.getScheduler()
                .buildTask(this, () -> PlaceholderHook.register(this))
                .delay(2, TimeUnit.SECONDS)
                .schedule();

        server.getScheduler()
                .buildTask(this, () -> PlaceholderHook.register(this))
                .delay(5, TimeUnit.SECONDS)
                .schedule();
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
