package com.shardedmc.lobbycore.manager;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class SpawnManager {

    private final ShardedLobbyCore plugin;
    private Location spawn;

    public SpawnManager(ShardedLobbyCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration config = plugin.getConfigManager().getModuleConfig("spawn");
        if (config == null) {
            return;
        }

        String worldName = config.getString("spawn.world");
        if (worldName == null || worldName.isEmpty()) {
            spawn = null;
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            spawn = null;
            return;
        }

        spawn = new Location(
                world,
                config.getDouble("spawn.x"),
                config.getDouble("spawn.y"),
                config.getDouble("spawn.z"),
                (float) config.getDouble("spawn.yaw"),
                (float) config.getDouble("spawn.pitch")
        );
    }

    public Location getSpawn() {
        if (spawn != null) {
            return spawn.clone();
        }
        World world = Bukkit.getWorlds().get(0);
        return world.getSpawnLocation();
    }

    public void setSpawn(Location location) {
        this.spawn = location.clone();
        FileConfiguration config = plugin.getConfigManager().getModuleConfig("spawn");
        config.set("spawn.world", location.getWorld().getName());
        config.set("spawn.x", location.getX());
        config.set("spawn.y", location.getY());
        config.set("spawn.z", location.getZ());
        config.set("spawn.yaw", location.getYaw());
        config.set("spawn.pitch", location.getPitch());
        plugin.getConfigManager().saveModuleConfig("spawn");
    }

    public boolean hasSpawn() {
        return spawn != null;
    }
}
