package dev.sharded.velocitycore.lobby;

import dev.sharded.velocitycore.lobby.expansion.ShardedExpansion;
import dev.sharded.velocitycore.lobby.hologram.HologramRefreshService;
import dev.sharded.velocitycore.lobby.sync.StatusCache;
import dev.sharded.velocitycore.lobby.sync.StatusSyncListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class LobbyPlugin extends JavaPlugin {

    private StatusCache statusCache;
    private HologramRefreshService hologramRefreshService;

    @Override
    public void onEnable() {
        this.statusCache = new StatusCache();
        this.hologramRefreshService = new HologramRefreshService(this);

        getServer().getMessenger().registerIncomingPluginChannel(
                this,
                "shardedvelocitycore:status",
                new StatusSyncListener(statusCache, hologramRefreshService)
        );
        getServer().getMessenger().registerOutgoingPluginChannel(this, "shardedvelocitycore:status");

        statusCache.addListener(hologramRefreshService::refreshAll);
        hologramRefreshService.start();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ShardedExpansion(statusCache).register();
            getLogger().info("Registered PlaceholderAPI placeholders: %shardedvelocitycore_status_<server>%");
        } else {
            getLogger().warning("PlaceholderAPI not found.");
        }

        getLogger().info("Lobby status sync enabled. Holograms refresh automatically every second.");
    }
}
