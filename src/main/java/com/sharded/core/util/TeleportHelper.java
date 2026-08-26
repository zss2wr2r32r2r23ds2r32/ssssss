package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Delayed teleport with cancel-on-move and actionbar countdown messages. */
public final class TeleportHelper implements Listener {

    public record Settings(
            int delaySeconds,
            String countdownActionbar,
            String cancelledActionbar,
            String countdownSound,
            String cancelSound,
            String successSound
    ) {
        public static Settings of(int delaySeconds, String countdownActionbar, String cancelledActionbar) {
            return new Settings(delaySeconds, countdownActionbar, cancelledActionbar,
                    "BLOCK_NOTE_BLOCK_PLING", "BLOCK_NOTE_BLOCK_BASS", "ENTITY_ENDERMAN_TELEPORT");
        }
    }

    private record Pending(Location start, BukkitTask task, Settings settings, Consumer<Player> onComplete) {
    }

    private final ShardedCore plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private boolean registered;

    public TeleportHelper(ShardedCore plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (registered) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registered = true;
    }

    public void unregister() {
        if (!registered) return;
        HandlerList.unregisterAll(this);
        registered = false;
    }

    public boolean isPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public void cancel(UUID uuid, boolean notify) {
        Pending pt = pending.remove(uuid);
        if (pt != null) pt.task().cancel();
        if (notify) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) notifyCancelled(player, pt == null ? null : pt.settings());
        }
    }

    public void cancel(Player player, boolean notify) {
        if (player != null) cancel(player.getUniqueId(), notify);
    }

    public void cancelAll() {
        for (UUID uuid : pending.keySet()) {
            cancel(uuid, false);
        }
        pending.clear();
    }

    public void begin(Player player, Location target, Settings settings, Consumer<Player> onComplete) {
        if (player == null || target == null || settings == null) return;
        cancel(player.getUniqueId(), false);
        Location start = player.getLocation().clone();
        int[] remaining = {settings.delaySeconds()};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancel(player.getUniqueId(), false);
                return;
            }
            if (remaining[0] <= 0) {
                cancel(player.getUniqueId(), false);
                player.teleportAsync(target).thenAccept(success -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (success) {
                        playSound(player, settings.successSound());
                        if (onComplete != null) onComplete.accept(player);
                    }
                }));
                return;
            }
            String bar = settings.countdownActionbar().replace("%seconds%", String.valueOf(remaining[0]));
            player.sendActionBar(Text.c(bar));
            playSound(player, settings.countdownSound());
            remaining[0]--;
        }, 0L, 20L);
        pending.put(player.getUniqueId(), new Pending(start, task, settings, onComplete));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        UUID uuid = event.getPlayer().getUniqueId();
        Pending pt = pending.get(uuid);
        if (pt == null) return;
        Location to = event.getTo();
        if (to != null && moved(pt.start(), to)) {
            cancel(uuid, true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId(), false);
    }

    private static boolean moved(Location from, Location to) {
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }

    private void notifyCancelled(Player player, Settings settings) {
        String bar = settings == null || settings.cancelledActionbar() == null
                ? "&#FF2727Teleport cancelled."
                : settings.cancelledActionbar();
        player.sendActionBar(Text.cPlain(bar));
        playSound(player, settings == null ? "BLOCK_NOTE_BLOCK_BASS" : settings.cancelSound());
    }

    private static void playSound(Player player, String raw) {
        if (raw == null || raw.isBlank()) return;
        try {
            player.playSound(player.getLocation(), Sound.valueOf(raw.toUpperCase(Locale.ROOT)), 0.8f, 0.8f);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
