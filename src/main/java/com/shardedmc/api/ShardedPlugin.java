package com.shardedmc.api;

/**
 * Base plugin interface compatible with ShardedMC's API layer.
 * Mirrors Bukkit/Spigot lifecycle patterns where feasible.
 */
public interface ShardedPlugin {

    String getName();

    String getVersion();

    void onEnable();

    void onDisable();

    default String getDescription() {
        return "";
    }
}
