package dev.sharded.velocitycore.status;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.sharded.velocitycore.ServerState;
import dev.sharded.velocitycore.config.PluginConfig;
import dev.sharded.velocitycore.util.ServerResolver;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ServerStatusManager {

    private final ProxyServer server;
    private final PluginConfig config;
    private final WhitelistTracker whitelistTracker = new WhitelistTracker();
    private final Map<String, ServerState> states = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ShardedVelocityCore-status");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Runnable changeListener;

    public ServerStatusManager(ProxyServer server, PluginConfig config) {
        this.server = server;
        this.config = config;
        for (String tracked : config.trackedServers()) {
            states.put(normalize(tracked), ServerState.OFFLINE);
        }
    }

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
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

    public boolean refreshNow() {
        Map<String, ServerState> previous = snapshot();
        for (String tracked : config.trackedServers()) {
            String key = normalize(tracked);
            states.put(key, resolveState(tracked));
        }
        if (!previous.equals(states)) {
            Runnable listener = changeListener;
            if (listener != null) {
                listener.run();
            }
            return true;
        }
        return false;
    }

    public boolean markWhitelisted(String serverName) {
        return updateWhitelistReport(serverName, true);
    }

    public boolean updateWhitelistReport(String serverName, boolean whitelisted) {
        boolean wasWhitelisted = whitelistTracker.isWhitelisted(serverName);
        whitelistTracker.setWhitelisted(serverName, whitelisted);

        String key = normalize(ServerResolver.canonicalName(server, serverName));
        ServerState newState = resolveState(serverName);
        ServerState previous = states.get(key);
        states.put(key, newState);

        boolean changed = previous != newState || wasWhitelisted != whitelisted;
        if (changed) {
            Runnable listener = changeListener;
            if (listener != null) {
                listener.run();
            }
        }
        return changed;
    }

    public ServerState getState(String serverName) {
        return states.getOrDefault(normalize(serverName), ServerState.OFFLINE);
    }

    public String getStatusPlaceholder(String serverName) {
        return getState(serverName).display();
    }

    public Map<String, ServerState> snapshot() {
        return new HashMap<>(states);
    }

    public boolean whitelistAsMaintenance() {
        return config.whitelistAsMaintenance();
    }

    public boolean isReachable(String serverName) {
        RegisteredServer registeredServer = ServerResolver.find(server, serverName).orElse(null);
        if (registeredServer == null) {
            return false;
        }
        try {
            registeredServer.ping().join();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean isJoinable(String serverName) {
        if (config.isLobby(serverName)) {
            return isReachable(serverName);
        }
        return getState(serverName) == ServerState.ONLINE && !isFull(serverName);
    }

    public boolean isFull(String serverName) {
        RegisteredServer registeredServer = ServerResolver.find(server, serverName).orElse(null);
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

        RegisteredServer registeredServer = ServerResolver.find(server, serverName).orElse(null);
        if (registeredServer == null) {
            whitelistTracker.clear(serverName);
            return ServerState.OFFLINE;
        }

        try {
            registeredServer.ping().join();
            if (config.whitelistAsMaintenance() && whitelistTracker.isWhitelisted(serverName)) {
                return ServerState.MAINTENANCE;
            }
            return ServerState.ONLINE;
        } catch (Exception ignored) {
            if (config.whitelistAsMaintenance() && whitelistTracker.isWhitelisted(serverName)) {
                return ServerState.MAINTENANCE;
            }
            return ServerState.OFFLINE;
        }
    }

    public static String normalize(String serverName) {
        return serverName.toLowerCase(Locale.ROOT);
    }
}
