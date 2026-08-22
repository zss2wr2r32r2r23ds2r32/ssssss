package dev.sharded.velocitycore.placeholder;

import dev.sharded.velocitycore.placeholder.expansion.ShardedExpansion;
import dev.sharded.velocitycore.placeholder.sync.StatusCache;
import dev.sharded.velocitycore.placeholder.sync.StatusSyncListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShardedPlaceholderPlugin extends JavaPlugin {

    private StatusCache statusCache;

    @Override
    public void onEnable() {
        this.statusCache = new StatusCache();
        getServer().getMessenger().registerIncomingPluginChannel(this, dev.sharded.velocitycore.common.PluginChannels.STATUS_CHANNEL, new StatusSyncListener(statusCache));
        getServer().getMessenger().registerOutgoingPluginChannel(this, dev.sharded.velocitycore.common.PluginChannels.STATUS_CHANNEL);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ShardedExpansion(statusCache).register();
            getLogger().info("Registered PlaceholderAPI expansion: shardedvelocitycore");
        } else {
            getLogger().warning("PlaceholderAPI not found. Status placeholders will not be available.");
        }
    }

    public StatusCache statusCache() {
        return statusCache;
    }
}
