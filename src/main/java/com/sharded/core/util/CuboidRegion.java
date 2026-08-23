package com.sharded.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** Axis-aligned cuboid in one world. */
public final class CuboidRegion {

    private final String world;
    private final int minX, minY, minZ, maxX, maxY, maxZ;

    public CuboidRegion(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public static CuboidRegion fromSection(ConfigurationSection section) {
        if (section == null || !section.isString("world")) return null;
        return new CuboidRegion(
                section.getString("world"),
                section.getInt("x1"), section.getInt("y1"), section.getInt("z1"),
                section.getInt("x2"), section.getInt("y2"), section.getInt("z2"));
    }

    public void write(ConfigurationSection section) {
        section.set("world", world);
        section.set("x1", minX);
        section.set("y1", minY);
        section.set("z1", minZ);
        section.set("x2", maxX);
        section.set("y2", maxY);
        section.set("z2", maxZ);
    }

    public String world() {
        return world;
    }

    public World bukkitWorld() {
        return Bukkit.getWorld(world);
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(Player player) {
        return contains(player.getLocation());
    }

    public int volume() {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }
}
