package dev.shardedsmp.listener;

import dev.shardedsmp.ShardedSMP;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerConnectionListener implements Listener {
    private final ShardedSMP plugin;

    public PlayerConnectionListener(ShardedSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.game().graceActive()
                && event.getPlayer().getGameMode() != GameMode.SPECTATOR
                && !plugin.game().hasReceivedSteak(event.getPlayer())) {
            plugin.game().setupPlayerForGrace(event.getPlayer());
        }
        for (ItemStack item : event.getPlayer().getInventory().getContents()) {
            plugin.listenerEnchant().cap(item);
        }
        plugin.game().updateDragonEggHearts(event.getPlayer());
        plugin.game().tickGlowAndHearts();
        plugin.questManager().showTo(event.getPlayer());
        plugin.questManager().updateBossBar();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.game().graceActive() || event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        org.bukkit.World world = plugin.game().overworld();
        if (world != null) {
            event.setRespawnLocation(dev.shardedsmp.util.LocationUtil.randomSafeLocation(world, plugin.game().borderPadding(), 50));
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.game().ensureSteak(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.glowManager().setPlayerGlowing(event.getPlayer(), false);
    }
}
