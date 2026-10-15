package dev.shardedsmp.util;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class LocationUtil {
    private static final Set<Material> UNSAFE_FLOOR = Set.of(
            Material.LAVA,
            Material.WATER,
            Material.CACTUS,
            Material.MAGMA_BLOCK,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.SWEET_BERRY_BUSH,
            Material.POWDER_SNOW,
            Material.KELP,
            Material.SEAGRASS,
            Material.TALL_SEAGRASS
    );

    private LocationUtil() {
    }

    public static Location randomLocationInBorder(World world, double padding) {
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double radius = Math.max(8.0, (border.getSize() / 2.0) - padding);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double x = center.getX() + random.nextDouble(-radius, radius);
        double z = center.getZ() + random.nextDouble(-radius, radius);
        return new Location(world, x, world.getMaxHeight() - 2, z);
    }

    public static Location randomSafeLocation(World world, double padding, int attempts) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double radius = Math.max(8.0, (border.getSize() / 2.0) - padding);

        for (int i = 0; i < attempts; i++) {
            int x = (int) Math.floor(center.getX() + random.nextDouble(-radius, radius));
            int z = (int) Math.floor(center.getZ() + random.nextDouble(-radius, radius));
            Location safe = findSafe(world, x, z);
            if (safe != null) {
                return safe;
            }
        }

        Location fallback = center.clone();
        fallback.setY(world.getHighestBlockYAt(fallback, HeightMap.MOTION_BLOCKING) + 1);
        ensurePlatform(fallback);
        fallback.setYaw(random.nextFloat() * 360f);
        return fallback;
    }

    public static Location findSafe(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        Block floor = world.getBlockAt(x, y, z);
        Block feet = floor.getRelative(BlockFace.UP);
        Block head = feet.getRelative(BlockFace.UP);
        if (!floor.getType().isSolid() || UNSAFE_FLOOR.contains(floor.getType())) {
            return null;
        }
        if (!feet.isPassable() || !head.isPassable()) {
            return null;
        }
        if (feet.isLiquid() || head.isLiquid()) {
            return null;
        }
        Location location = new Location(world, x + 0.5, y + 1.0, z + 0.5);
        location.setYaw(ThreadLocalRandom.current().nextFloat() * 360f);
        return location;
    }

    public static Location skyDropLocation(World world, double padding) {
        Location xz = randomSafeLocation(world, padding, 40);
        xz.setY(Math.min(world.getMaxHeight() - 2, xz.getY() + 40));
        return xz;
    }

    public static void ensurePlatform(Location location) {
        Block floor = location.getBlock().getRelative(BlockFace.DOWN);
        if (!floor.getType().isSolid() || UNSAFE_FLOOR.contains(floor.getType())) {
            floor.setType(Material.STONE);
        }
        location.getBlock().setType(Material.AIR);
        location.getBlock().getRelative(BlockFace.UP).setType(Material.AIR);
    }

    public static boolean isInsideBorder(Location location, double padding) {
        WorldBorder border = location.getWorld().getWorldBorder();
        double radius = (border.getSize() / 2.0) - padding;
        Location center = border.getCenter();
        return Math.abs(location.getX() - center.getX()) <= radius
                && Math.abs(location.getZ() - center.getZ()) <= radius;
    }
}
