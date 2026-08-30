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
import java.util.concurrent.atomic.AtomicInteger;

public final class ServerStatusManager {

    private static final long PING_TIMEOUT_MS = 2000L;
    private static final int OFFLINE_FAILURE_THRESHOLD = 3;

    private final ProxyServer server;
    private final PluginConfig config;
    private final WhitelistTracker whitelistTracker = new WhitelistTracker();
    private final Map<String, ServerState> states = new ConcurrentHashMap<>();
    private final Map<String, Boolean> reachable = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ShardedVelocityCore-status");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Runnable changeListener;

    public ServerStatusManager(ProxyServer server, PluginConfig config, WhitelistPersistence persistence) {
        this.server = server;
        this.config = config;
        whitelistTracker.load(persistence.load());
        whitelistTracker.setSaveListener(persistence::save);
        for (String tracked : config.trackedServers()) {
            String key = normalize(tracked);
            // Optimistic until proven otherwise — never block joins on ping.
            reachable.put(key, true);
            consecutiveFailures.put(key, new AtomicInteger(0));
            states.put(key, computeState(tracked));
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
            pingAsync(tracked);
            String key = normalize(tracked);
            states.put(key, computeState(tracked));
        }
        return notifyIfChanged(previous);
    }

    public boolean updateWhitelistReport(String serverName, boolean whitelisted) {
        boolean wasWhitelisted = whitelistTracker.isWhitelisted(serverName);
        whitelistTracker.setWhitelisted(serverName, whitelisted);

        String key = normalize(ServerResolver.canonicalName(server, serverName));
        Map<String, ServerState> previous = snapshot();
        states.put(key, computeState(serverName));

        boolean changed = !previous.equals(states) || wasWhitelisted != whitelisted;
        if (changed) {
            notifyChange();
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

    /**
     * Explicit maintenance from config {@code maintenance-servers} only.
     * Whitelist-as-maintenance is display-only and must not block joins.
     */
    public boolean isHardMaintenance(String serverName) {
        return config.maintenanceServers().contains(normalize(serverName));
    }

    public boolean isReachable(String serverName) {
        String key = normalize(serverName);
        if (Boolean.TRUE.equals(reachable.get(key))) {
            return true;
        }
        // If anyone is already connected through Velocity, treat as up.
        RegisteredServer registeredServer = ServerResolver.find(server, serverName).orElse(null);
        return registeredServer != null && !registeredServer.getPlayersConnected().isEmpty();
    }

    public boolean isJoinable(String serverName) {
        if (isHardMaintenance(serverName)) {
            return false;
        }
        if (config.isLobby(serverName)) {
            return true;
        }
        return !isFull(serverName);
    }

    public boolean isFull(String serverName) {
        RegisteredServer registeredServer = ServerResolver.find(server, serverName).orElse(null);
        if (registeredServer == null) {
            return true;
        }
        return registeredServer.getPlayersConnected().size() >= config.maxPlayers(normalize(serverName));
    }

    private void pingAsync(String serverName) {
        String key = normalize(serverName);
        RegisteredServer registeredServer = ServerResolver.find(server, serverName).orElse(null);
        if (registeredServer == null) {
            reachable.put(key, false);
            states.put(key, ServerState.OFFLINE);
            return;
        }

        // Players already online through the proxy means the server is up.
        if (!registeredServer.getPlayersConnected().isEmpty()) {
            consecutiveFailures.computeIfAbsent(key, ignored -> new AtomicInteger()).set(0);
            Boolean previous = reachable.put(key, true);
            ServerState previousState = states.put(key, computeState(serverName));
            if (previous == null || !previous || previousState != states.get(key)) {
                notifyChange();
            }
            return;
        }

        registeredServer.ping()
                .orTimeout(PING_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .whenComplete((ignored, error) -> {
                    AtomicInteger failures = consecutiveFailures.computeIfAbsent(key, k -> new AtomicInteger());
                    boolean up;
                    if (error == null) {
                        failures.set(0);
                        up = true;
                    } else {
                        up = failures.incrementAndGet() < OFFLINE_FAILURE_THRESHOLD;
                    }
                    Boolean previous = reachable.put(key, up);
                    ServerState previousState = states.put(key, computeState(serverName));
                    if (previous == null || previous.booleanValue() != up
                            || previousState != states.get(key)) {
                        notifyChange();
                    }
                });
    }

    private ServerState computeState(String serverName) {
        String normalized = normalize(serverName);
        if (config.maintenanceServers().contains(normalized)) {
            return ServerState.MAINTENANCE;
        }

        RegisteredServer registeredServer = ServerResolver.find(server, serverName).orElse(null);
        if (registeredServer == null) {
            return ServerState.OFFLINE;
        }

        if (config.whitelistAsMaintenance() && whitelistTracker.isWhitelisted(serverName)) {
            return ServerState.MAINTENANCE;
        }

        if (!isReachable(serverName)) {
            return ServerState.OFFLINE;
        }

        return ServerState.ONLINE;
    }

    private boolean notifyIfChanged(Map<String, ServerState> previous) {
        if (!previous.equals(states)) {
            notifyChange();
            return true;
        }
        return false;
    }

    private void notifyChange() {
        Runnable listener = changeListener;
        if (listener != null) {
            listener.run();
        }
    }

    public static String normalize(String serverName) {
        return serverName.toLowerCase(Locale.ROOT);
    }
}
