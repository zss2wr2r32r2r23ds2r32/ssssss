package dev.sharded.velocitycore.status;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.sharded.velocitycore.ServerState;
import dev.sharded.velocitycore.config.PluginConfig;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ServerStatusManager {

    private final ProxyServer server;
    private final PluginConfig config;
    private final Map<String, ServerState> states = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ShardedVelocityCore-status");
        thread.setDaemon(true);
        return thread;
    });

    public ServerStatusManager(ProxyServer server, PluginConfig config) {
        this.server = server;
        this.config = config;
        for (String tracked : config.trackedServers()) {
            states.put(normalize(tracked), ServerState.OFFLINE);
        }
    }

    public void start() {
        refreshNow();
        executor.scheduleAtFixedRate(
                this::refreshNow,
                config.statusRefreshSeconds(),
                config.statusRefreshSeconds(),
                TimeUnit.SECONDS
        );
    }

    public void stop() {
        executor.shutdownNow();
    }

    public void refreshNow() {
        for (String tracked : config.trackedServers()) {
            String key = normalize(tracked);
            states.put(key, resolveState(tracked));
        }
    }

    public ServerState getState(String serverName) {
        return states.getOrDefault(normalize(serverName), ServerState.OFFLINE);
    }

    public String getStatusPlaceholder(String serverName) {
        return getState(serverName).display();
    }

    public boolean isJoinable(String serverName) {
        return getState(serverName) == ServerState.ONLINE && !isFull(serverName);
    }

    public boolean isFull(String serverName) {
        RegisteredServer registeredServer = server.getServer(serverName).orElse(null);
        if (registeredServer == null) {
            return true;
        }
        return registeredServer.getPlayersConnected().size() >= config.maxPlayers(normalize(serverName));
    }

    private ServerState resolveState(String serverName) {
        String normalized = normalize(serverName);
        if (config.maintenanceServers().contains(normalized)) {
            return ServerState.MAINTENANCE;
        }

        RegisteredServer registeredServer = server.getServer(serverName).orElse(null);
        if (registeredServer == null) {
            return ServerState.OFFLINE;
        }

        try {
            registeredServer.ping().join();
            return ServerState.ONLINE;
        } catch (Exception ignored) {
            return ServerState.OFFLINE;
        }
    }

    public static String normalize(String serverName) {
        return serverName.toLowerCase(Locale.ROOT);
    }
}
