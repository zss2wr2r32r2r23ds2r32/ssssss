package dev.sharded.velocitycore.status;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.sharded.velocitycore.common.PluginChannels;
import dev.sharded.velocitycore.config.PluginConfig;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MaintenanceRequestService {

    private static final byte[] REQUEST_PAYLOAD = new byte[]{MaintenanceMessages.REQUEST};
    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(PluginChannels.MAINTENANCE_CHANNEL);

    private final ProxyServer server;
    private final PluginConfig config;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ShardedVelocityCore-maintenance-request");
        thread.setDaemon(true);
        return thread;
    });

    public MaintenanceRequestService(ProxyServer server, PluginConfig config) {
        this.server = server;
        this.config = config;
    }

    public void start() {
        requestLobby();
        executor.scheduleAtFixedRate(
                this::requestLobby,
                config.statusRefreshSeconds(),
                config.statusRefreshSeconds(),
                TimeUnit.SECONDS
        );
    }

    public void stop() {
        executor.shutdownNow();
    }

    public void requestLobby() {
        server.getServer(config.lobbyServer()).ifPresent(this::request);
        server.getAllServers().stream()
                .filter(registered -> registered.getServerInfo().getName().equalsIgnoreCase(config.lobbyServer()))
                .findFirst()
                .ifPresent(this::request);
    }

    private void request(RegisteredServer registeredServer) {
        try {
            registeredServer.sendPluginMessage(CHANNEL, REQUEST_PAYLOAD);
        } catch (Exception ignored) {
            // Lobby may be offline.
        }
    }
}
