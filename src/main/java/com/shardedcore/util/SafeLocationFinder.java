package com.shardedcore.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.concurrent.ThreadLocalRandom;

public final class SafeLocationFinder {

    private SafeLocationFinder() {}

    public static Location find(World world, ConfigurationSection settings) {
        if (world == null || settings == null) return null;
        return find(world, settings.getInt("center-x", 0), settings.getInt("center-z", 0),
                settings.getInt("min-radius", 100), settings.getInt("max-radius", 500),
                settings.getInt("max-attempts", 25));
    }

    public static Location find(World world, int centerX, int centerZ, int minRadius, int maxRadius, int attempts) {
        if (world == null) return null;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < attempts; i++) {
            int distance = random.nextInt(minRadius, Math.max(minRadius + 1, maxRadius));
            double angle = random.nextDouble() * Math.PI * 2;
            int x = centerX + (int) (Math.cos(angle) * distance);
            int z = centerZ + (int) (Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight()) continue;
            Block ground = world.getBlockAt(x, y, z);
            Material type = ground.getType();
            if (type == Material.LAVA || type == Material.WATER || type == Material.CACTUS
                    || type == Material.MAGMA_BLOCK || type.isAir()) continue;
            if (!world.getBlockAt(x, y + 1, z).getType().isAir()) continue;
            return new Location(world, x + 0.5, y + 1.0, z + 0.5);
        }
        return null;
    }
}
