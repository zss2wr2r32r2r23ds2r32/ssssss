package com.sharded.core.util;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** pos1 / pos2 selection helper for region commands. */
public final class RegionSetup {

    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public void setPos1(Player player, Location loc) {
        pos1.put(player.getUniqueId(), loc.clone());
    }

    public void setPos2(Player player, Location loc) {
        pos2.put(player.getUniqueId(), loc.clone());
    }

    public Location pos1(Player player) {
        return pos1.get(player.getUniqueId());
    }

    public Location pos2(Player player) {
        return pos2.get(player.getUniqueId());
    }

    public CuboidRegion build(Player player) {
        Location a = pos1(player);
        Location b = pos2(player);
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return null;
        if (!a.getWorld().equals(b.getWorld())) return null;
        return new CuboidRegion(
                a.getWorld().getName(),
                a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    public void saveRegion(YamlConfiguration config, String path, CuboidRegion region, File file) throws Exception {
        config.createSection(path).set("world", null);
        region.write(config.getConfigurationSection(path));
        config.save(file);
    }
}
