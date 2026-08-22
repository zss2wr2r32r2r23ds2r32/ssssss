package dev.sharded.velocitycore.velocity.status;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.sharded.velocitycore.common.ServerState;
import dev.sharded.velocitycore.velocity.config.PluginConfig;

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
        Thread thread = new Thread(r, "svc-status");
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

    public Map<String, ServerState> snapshot() {
        return Map.copyOf(states);
    }

    public boolean isJoinable(String serverName) {
        return getState(serverName) == ServerState.ONLINE && !isFull(serverName);
    }

    public boolean isFull(String serverName) {
        RegisteredServer registeredServer = server.getServer(serverName).orElse(null);
        if (registeredServer == null) {
            return true;
        }
        int online = registeredServer.getPlayersConnected().size();
        return online >= config.maxPlayers(normalize(serverName));
    }

    public int onlineCount(String serverName) {
        return server.getServer(serverName)
                .map(registered -> registered.getPlayersConnected().size())
                .orElse(0);
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
