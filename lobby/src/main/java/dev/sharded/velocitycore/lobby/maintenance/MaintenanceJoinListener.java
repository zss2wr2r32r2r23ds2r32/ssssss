package dev.sharded.velocitycore.lobby.maintenance;

import dev.sharded.velocitycore.lobby.util.DisconnectUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

public final class MaintenanceJoinListener implements Listener {

    private final MaintenanceManager maintenanceManager;

    public MaintenanceJoinListener(MaintenanceManager maintenanceManager) {
        this.maintenanceManager = maintenanceManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (maintenanceManager.canJoin(event.getPlayer())) {
            return;
        }
        event.disallow(
                PlayerLoginEvent.Result.KICK_OTHER,
                maintenanceManager.kickComponent()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (maintenanceManager.canJoin(event.getPlayer())) {
            return;
        }
        DisconnectUtil.disconnect(event.getPlayer(), maintenanceManager.kickComponent());
    }
}
