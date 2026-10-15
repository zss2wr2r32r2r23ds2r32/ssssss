package dev.sharded.core.spawn;

import dev.sharded.core.ShardedCore;
import dev.sharded.core.hook.WorldGuardHook;
import dev.sharded.core.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class SpawnManager {
    private final ShardedCore plugin;
    private Location mainSpawn;
    private int regionRadius = 50;

    public SpawnManager(ShardedCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration config = plugin.getConfig();
        regionRadius = Math.max(1, config.getInt("spawn.region-radius", 50));
        mainSpawn = Locations.read(config.getConfigurationSection("spawn.location"));
    }

    public void setMainSpawn(Location location) {
        this.mainSpawn = location.clone();
        Locations.write(plugin.getConfig().createSection("spawn.location"), location);
        plugin.saveConfig();
    }

    public Location getMainSpawn() {
        return mainSpawn == null ? null : mainSpawn.clone();
    }

    public Location getEffectiveSpawn(World fallbackWorld) {
        if (mainSpawn != null && mainSpawn.getWorld() != null) {
            return mainSpawn.clone();
        }
        World world = fallbackWorld != null ? fallbackWorld : Bukkit.getWorlds().getFirst();
        return world.getSpawnLocation().clone().add(0.5, 0, 0.5);
    }

    public int regionRadius() {
        return regionRadius;
    }

    public boolean isInSpawnRegion(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (WorldGuardHook.isInNamedRegion(location, "spawn")) {
            return true;
        }
        Location center = mainSpawn != null ? mainSpawn : location.getWorld().getSpawnLocation();
        if (center.getWorld() == null || !center.getWorld().equals(location.getWorld())) {
            return false;
        }
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        return (dx * dx + dz * dz) <= (double) regionRadius * regionRadius;
    }

    public void teleport(Player player) {
        Location dest = getEffectiveSpawn(player.getWorld());
        player.teleportAsync(dest);
        if (getMainSpawn() == null) {
            player.sendMessage(plugin.messages().get("spawn.not-set"));
        } else {
            player.sendMessage(plugin.messages().get("spawn.teleported"));
        }
    }
}
