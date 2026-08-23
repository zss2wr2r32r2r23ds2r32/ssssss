package dev.sharded.velocitycore.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import dev.sharded.velocitycore.config.PluginConfig;
import dev.sharded.velocitycore.status.ServerStatusManager;
import dev.sharded.velocitycore.util.LegacyText;
import dev.sharded.velocitycore.util.ServerResolver;
import net.kyori.adventure.text.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QueueManager {

    private final ProxyServer server;
    private final PluginConfig config;
    private final ServerStatusManager statusManager;
    private final Map<String, Deque<UUID>> queues = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerQueues = new ConcurrentHashMap<>();
    private ScheduledTask actionBarTask;

    public QueueManager(ProxyServer server, PluginConfig config, ServerStatusManager statusManager) {
        this.server = server;
        this.config = config;
        this.statusManager = statusManager;
        for (String queueServer : config.queueServers()) {
            queues.put(ServerStatusManager.normalize(queueServer), new ArrayDeque<>());
        }
    }

    public void start(Object plugin) {
        actionBarTask = server.getScheduler()
                .buildTask(plugin, this::refreshActionBars)
                .repeat(config.actionBarIntervalTicks() * 50L, java.util.concurrent.TimeUnit.MILLISECONDS)
                .schedule();
    }

    public void stop() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
    }

    public boolean joinQueue(Player player, String serverName) {
        Optional<com.velocitypowered.api.proxy.server.RegisteredServer> registered = ServerResolver.find(server, serverName);
        if (registered.isEmpty()) {
            return false;
        }

        String canonical = registered.get().getServerInfo().getName();
        String normalized = ServerStatusManager.normalize(canonical);
        queues.computeIfAbsent(normalized, ignored -> new ArrayDeque<>());

        leaveQueue(player);

        if (config.isLobby(canonical)) {
            if (statusManager.isReachable(canonical)) {
                connect(player, canonical);
                return true;
            }
            return false;
        }

        if (statusManager.isJoinable(canonical)) {
            connect(player, canonical);
            return true;
        }

        Deque<UUID> queue = queues.get(normalized);
        queue.addLast(player.getUniqueId());
        playerQueues.put(player.getUniqueId(), normalized);
        sendActionBar(player, canonical);
        return true;
    }

    public boolean isQueued(UUID playerId) {
        return playerQueues.containsKey(playerId);
    }

    public void leaveQueue(Player player) {
        String server = playerQueues.remove(player.getUniqueId());
        if (server == null) {
            return;
        }
        Deque<UUID> queue = queues.get(server);
        if (queue != null) {
            queue.remove(player.getUniqueId());
        }
    }

    public Optional<String> queuedServer(UUID playerId) {
        return Optional.ofNullable(playerQueues.get(playerId));
    }

    public int position(UUID playerId) {
        String server = playerQueues.get(playerId);
        if (server == null) {
            return 0;
        }
        Deque<UUID> queue = queues.get(server);
        if (queue == null) {
            return 0;
        }
        int index = 1;
        for (UUID queued : queue) {
            if (queued.equals(playerId)) {
                return index;
            }
            index++;
        }
        return 0;
    }

    public int waitingCount(String serverName) {
        String normalized = ServerStatusManager.normalize(ServerResolver.canonicalName(server, serverName));
        Deque<UUID> queue = queues.get(normalized);
        return queue == null ? 0 : queue.size();
    }

    public void processQueues() {
        for (String serverName : new ArrayList<>(queues.keySet())) {
            String canonical = ServerResolver.canonicalName(server, serverName);
            if (!statusManager.isJoinable(canonical)) {
                continue;
            }
            Deque<UUID> queue = queues.get(serverName);
            if (queue == null || queue.isEmpty()) {
                continue;
            }
            UUID next = queue.peekFirst();
            if (next == null) {
                continue;
            }
            server.getPlayer(next).ifPresent(player -> {
                queue.pollFirst();
                playerQueues.remove(next);
                connect(player, canonical);
            });
        }
    }

    public Component format(String message) {
        return LegacyText.parse(config.queuePrefix() + message);
    }

    public String serverColor(String serverName) {
        return config.queueColors().serverColor(serverName);
    }

    public String formatActionBar(String serverName, int position, int waiting) {
        return config.queueActionBar()
                .replace("%numberinqueue%", String.valueOf(position))
                .replace("%server%", serverName)
                .replace("%server_color%", serverColor(serverName))
                .replace("%numberofpeoplewaitinginqueue%", String.valueOf(waiting));
    }

    private void connect(Player player, String serverName) {
        ServerResolver.find(server, serverName).ifPresentOrElse(
                target -> player.createConnectionRequest(target).fireAndForget(),
                () -> player.sendMessage(format(
                        config.queueColors().error() + "Server &n" + serverName + "&r "
                                + config.queueColors().error() + "is unavailable."
                ))
        );
    }

    private void refreshActionBars() {
        processQueues();
        for (Map.Entry<UUID, String> entry : new HashMap<>(playerQueues).entrySet()) {
            server.getPlayer(entry.getKey()).ifPresent(player -> {
                String canonical = ServerResolver.canonicalName(server, entry.getValue());
                sendActionBar(player, canonical);
            });
        }
    }

    private void sendActionBar(Player player, String serverName) {
        int position = position(player.getUniqueId());
        int waiting = waitingCount(serverName);
        player.sendActionBar(LegacyText.parse(formatActionBar(serverName, position, waiting)));
    }

    public ServerStatusManager statusManager() {
        return statusManager;
    }
}
