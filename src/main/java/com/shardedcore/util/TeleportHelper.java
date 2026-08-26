package com.shardedcore.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class TeleportHelper implements Listener {

    private final Plugin plugin;
    private final Map<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();
    private BukkitTask ticker;

    public TeleportHelper(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (ticker != null) {
            ticker.cancel();
        }
        pending.clear();
    }

    public void teleportDelayed(Player player, Location destination, int delaySeconds,
                                String countdownMessage, Consumer<Player> onComplete,
                                Runnable onCancel) {
        cancel(player.getUniqueId());
        PendingTeleport teleport = new PendingTeleport(player.getUniqueId(), destination.clone(),
                delaySeconds * 20, countdownMessage, onComplete, onCancel);
        pending.put(player.getUniqueId(), teleport);
    }

    public void cancel(UUID playerId) {
        PendingTeleport teleport = pending.remove(playerId);
        if (teleport != null && teleport.onCancel != null) {
            teleport.onCancel.run();
        }
    }

    private void tick() {
        pending.values().removeIf(teleport -> {
            Player player = plugin.getServer().getPlayer(teleport.playerId);
            if (player == null || !player.isOnline()) {
                return true;
            }
            if (--teleport.ticksRemaining <= 0) {
                player.teleportAsync(teleport.destination);
                if (teleport.onComplete != null) {
                    teleport.onComplete.accept(player);
                }
                return true;
            }
            if (teleport.countdownMessage != null && teleport.ticksRemaining % 20 == 0) {
                int seconds = teleport.ticksRemaining / 20;
                player.sendActionBar(Text.cPlain(teleport.countdownMessage
                        .replace("{seconds}", String.valueOf(seconds))
                        .replace("%seconds%", String.valueOf(seconds))));
            }
            return false;
        });
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        PendingTeleport teleport = pending.remove(event.getPlayer().getUniqueId());
        if (teleport != null) {
            Player player = event.getPlayer();
            player.sendActionBar(Text.component("<red>Teleport cancelled.</red>", player));
            if (teleport.onCancel != null) {
                teleport.onCancel.run();
            }
        }
    }

    private static final class PendingTeleport {
        private final UUID playerId;
        private final Location destination;
        private int ticksRemaining;
        private final String countdownMessage;
        private final Consumer<Player> onComplete;
        private final Runnable onCancel;

        private PendingTeleport(UUID playerId, Location destination, int ticksRemaining,
                                String countdownMessage, Consumer<Player> onComplete, Runnable onCancel) {
            this.playerId = playerId;
            this.destination = destination;
            this.ticksRemaining = ticksRemaining;
            this.countdownMessage = countdownMessage;
            this.onComplete = onComplete;
            this.onCancel = onCancel;
        }
    }
}
