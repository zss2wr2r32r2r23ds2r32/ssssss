package dev.sharded.velocitycore.velocity.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import dev.sharded.velocitycore.velocity.config.PluginConfig;
import dev.sharded.velocitycore.velocity.status.ServerStatusManager;
import dev.sharded.velocitycore.velocity.util.LegacyText;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QueueManager {

    private final ProxyServer server;
    private final Logger logger;
    private final PluginConfig config;
    private final ServerStatusManager statusManager;
    private final Map<String, Deque<UUID>> queues = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerQueues = new ConcurrentHashMap<>();
    private ScheduledTask actionBarTask;

    public QueueManager(ProxyServer server, Logger logger, PluginConfig config, ServerStatusManager statusManager) {
        this.server = server;
        this.logger = logger;
        this.config = config;
        this.statusManager = statusManager;
        for (String tracked : config.trackedServers()) {
            queues.put(ServerStatusManager.normalize(tracked), new ArrayDeque<>());
        }
    }

    public void start() {
        actionBarTask = server.getScheduler()
                .buildTask(this, this::refreshActionBars)
                .repeat(config.actionBarIntervalTicks() * 50L, java.util.concurrent.TimeUnit.MILLISECONDS)
                .schedule();
    }

    public void stop() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
    }

    public boolean joinQueue(Player player, String serverName) {
        String normalized = ServerStatusManager.normalize(serverName);
        if (!queues.containsKey(normalized)) {
            return false;
        }

        leaveQueue(player);

        if (statusManager.isJoinable(normalized)) {
            connect(player, normalized);
            return true;
        }

        Deque<UUID> queue = queues.computeIfAbsent(normalized, ignored -> new ArrayDeque<>());
        queue.addLast(player.getUniqueId());
        playerQueues.put(player.getUniqueId(), normalized);
        sendActionBar(player, normalized);
        return true;
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
        Deque<UUID> queue = queues.get(ServerStatusManager.normalize(serverName));
        return queue == null ? 0 : queue.size();
    }

    public void processQueues() {
        for (String serverName : new ArrayList<>(queues.keySet())) {
            if (!statusManager.isJoinable(serverName)) {
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
                connect(player, serverName);
            });
        }
    }

    private void connect(Player player, String serverName) {
        server.getServer(serverName).ifPresentOrElse(
                target -> player.createConnectionRequest(target).fireAndForget(),
                () -> player.sendMessage(LegacyText.parse(config.queuePrefix() + "&#FF0000Server &n" + serverName + "&r &#FF0000is unavailable."))
        );
    }

    private void refreshActionBars() {
        processQueues();
        for (Map.Entry<UUID, String> entry : new HashMap<>(playerQueues).entrySet()) {
            server.getPlayer(entry.getKey()).ifPresent(player -> sendActionBar(player, entry.getValue()));
        }
    }

    private void sendActionBar(Player player, String serverName) {
        int position = position(player.getUniqueId());
        int waiting = waitingCount(serverName);
        String formatted = config.queueActionBar()
                .replace("%numberinqueue%", String.valueOf(position))
                .replace("%server%", serverName)
                .replace("%numberofpeoplewaitinginqueue%", String.valueOf(waiting));
        player.sendActionBar(LegacyText.parse(formatted));
    }

    public String formatQueueMessage(String message) {
        return config.queuePrefix() + message;
    }

    public ServerStatusManager statusManager() {
        return statusManager;
    }
}
