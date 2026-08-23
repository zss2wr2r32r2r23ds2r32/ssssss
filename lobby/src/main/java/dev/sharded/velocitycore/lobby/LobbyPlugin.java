package dev.sharded.velocitycore.lobby;

import dev.sharded.velocitycore.lobby.config.LobbySettings;
import dev.sharded.velocitycore.lobby.expansion.ShardedExpansion;
import dev.sharded.velocitycore.lobby.hologram.HologramRefreshService;
import dev.sharded.velocitycore.lobby.maintenance.MaintenanceCommand;
import dev.sharded.velocitycore.lobby.maintenance.MaintenanceJoinListener;
import dev.sharded.velocitycore.lobby.maintenance.MaintenanceManager;
import dev.sharded.velocitycore.lobby.maintenance.MaintenancePingListener;
import dev.sharded.velocitycore.lobby.maintenance.MaintenanceSyncService;
import dev.sharded.velocitycore.lobby.sync.StatusCache;
import dev.sharded.velocitycore.lobby.sync.StatusSyncListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class LobbyPlugin extends JavaPlugin {

    private StatusCache statusCache;
    private HologramRefreshService hologramRefreshService;
    private LobbySettings lobbySettings;
    private MaintenanceSyncService maintenanceSyncService;
    private MaintenanceManager maintenanceManager;

    @Override
    public void onEnable() {
        this.statusCache = new StatusCache();
        this.hologramRefreshService = new HologramRefreshService(this);
        this.lobbySettings = new LobbySettings(this);
        this.maintenanceSyncService = new MaintenanceSyncService(this, lobbySettings);
        this.maintenanceManager = new MaintenanceManager(this, lobbySettings, maintenanceSyncService);

        getServer().getMessenger().registerIncomingPluginChannel(
                this,
                "shardedvelocitycore:status",
                new StatusSyncListener(statusCache, hologramRefreshService)
        );
        getServer().getMessenger().registerOutgoingPluginChannel(this, "shardedvelocitycore:status");
        maintenanceSyncService.register();

        statusCache.addListener(hologramRefreshService::refreshAll);
        hologramRefreshService.start();

        MaintenanceCommand maintenanceCommand = new MaintenanceCommand(maintenanceManager);
        getCommand("maintenance").setExecutor(maintenanceCommand);
        getCommand("maintenance").setTabCompleter(maintenanceCommand);
        getServer().getPluginManager().registerEvents(new MaintenanceJoinListener(maintenanceManager), this);
        getServer().getPluginManager().registerEvents(new MaintenancePingListener(maintenanceManager, lobbySettings), this);

        maintenanceSyncService.syncNow(maintenanceManager.isEnabled());

        if (maintenanceManager.isEnabled()) {
            Bukkit.getScheduler().runTask(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!maintenanceManager.canJoin(player)) {
                        player.kick(maintenanceManager.kickComponent());
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

        getLogger().info("Lobby status sync and maintenance mode enabled.");
    }
}
