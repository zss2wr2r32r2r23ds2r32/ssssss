package dev.sharded.velocitycore.lobby;

import dev.sharded.velocitycore.lobby.config.MotdConfig;
import dev.sharded.velocitycore.lobby.expansion.ShardedExpansion;
import dev.sharded.velocitycore.lobby.hologram.HologramRefreshService;
import dev.sharded.velocitycore.lobby.maintenance.MaintenanceCommand;
import dev.sharded.velocitycore.lobby.maintenance.MaintenanceJoinListener;
import dev.sharded.velocitycore.lobby.maintenance.MaintenanceManager;
import dev.sharded.velocitycore.lobby.maintenance.MaintenanceSyncService;
import dev.sharded.velocitycore.lobby.motd.HoverService;
import dev.sharded.velocitycore.lobby.motd.MotdPingListener;
import dev.sharded.velocitycore.lobby.motd.MotdService;
import dev.sharded.velocitycore.lobby.motd.ServerIconService;
import dev.sharded.velocitycore.lobby.sync.StatusCache;
import dev.sharded.velocitycore.lobby.sync.StatusSyncListener;
import dev.sharded.velocitycore.lobby.util.DisconnectUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class LobbyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        StatusCache statusCache = new StatusCache();
        HologramRefreshService hologramRefreshService = new HologramRefreshService(this);
        MotdConfig motdConfig = new MotdConfig(this);
        MotdService motdService = new MotdService(motdConfig);
        ServerIconService iconService = new ServerIconService(this);
        HoverService hoverService = new HoverService();
        MaintenanceSyncService maintenanceSyncService = new MaintenanceSyncService(this, motdService);
        MaintenanceManager maintenanceManager = new MaintenanceManager(this, motdService, maintenanceSyncService);

        getServer().getMessenger().registerIncomingPluginChannel(
                this,
                "shardedvelocitycore:status",
                new StatusSyncListener(statusCache, hologramRefreshService)
        );
        getServer().getMessenger().registerOutgoingPluginChannel(this, "shardedvelocitycore:status");
        maintenanceSyncService.register();

        MaintenanceCommand maintenanceCommand = new MaintenanceCommand(maintenanceManager);
        getCommand("maintenance").setExecutor(maintenanceCommand);
        getCommand("maintenance").setTabCompleter(maintenanceCommand);
        getServer().getPluginManager().registerEvents(new MaintenanceJoinListener(maintenanceManager), this);
        getServer().getPluginManager().registerEvents(
                new MotdPingListener(maintenanceManager, motdService, iconService, hoverService),
                this
        );

        maintenanceSyncService.syncNow(maintenanceManager.isEnabled());

        if (maintenanceManager.isEnabled()) {
            Bukkit.getScheduler().runTask(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!maintenanceManager.canJoin(player)) {
                        DisconnectUtil.disconnect(player, maintenanceManager.kickComponent());
                    }
                }
            });
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ShardedExpansion(statusCache).register();
            getLogger().info("Registered PlaceholderAPI placeholders: %shardedvelocitycore_status_<server>%");
        } else {
            getLogger().warning("PlaceholderAPI not found.");
        }

        getLogger().info("Lobby MOTD, hover, and maintenance mode enabled.");
    }
}
