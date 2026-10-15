package dev.sharded.core.spawn;

import dev.sharded.core.ShardedCore;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public final class FirstJoinListener implements Listener {
    private final ShardedCore plugin;

    public FirstJoinListener(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnLocation(PlayerSpawnLocationEvent event) {
        if (!plugin.getConfig().getBoolean("first-join.teleport", true)) {
            return;
        }
        if (!plugin.players().isFirstJoin(event.getPlayer().getUniqueId())) {
            return;
        }
        Location dest = plugin.spawns().getEffectiveSpawn(event.getSpawnLocation().getWorld());
        event.setSpawnLocation(dest);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean first = plugin.players().isFirstJoin(player.getUniqueId());
        if (!first || !plugin.getConfig().getBoolean("first-join.teleport", true)) {
            if (first) {
                plugin.players().remember(player);
            }
            return;
        }
        Location dest = plugin.spawns().getEffectiveSpawn(player.getWorld());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.teleportAsync(dest);
                player.sendMessage(plugin.messages().get("spawn.first-join"));
                plugin.players().remember(player);
            }
        });
    }
}
