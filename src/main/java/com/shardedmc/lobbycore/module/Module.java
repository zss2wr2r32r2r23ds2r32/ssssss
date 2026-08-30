package com.shardedmc.lobbycore.module;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import org.bukkit.configuration.file.FileConfiguration;

public interface Module {

    String getId();

    String getDisplayName();

    void enable(ShardedLobbyCore plugin, FileConfiguration config);

    void disable();

    default void reload(ShardedLobbyCore plugin, FileConfiguration config) {
        disable();
        enable(plugin, config);
    }
}
