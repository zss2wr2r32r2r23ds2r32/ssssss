package com.sharded.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** Serialize/deserialize Bukkit locations to YAML sections. */
public final class LocationUtil {

    private LocationUtil() {
    }

    public static void write(ConfigurationSection section, Location location) {
        if (section == null || location == null || location.getWorld() == null) return;
        section.set("world", location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }

    public static Location read(ConfigurationSection section) {
        if (section == null || !section.isString("world")) return null;
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) return null;
        return new Location(world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }
}
