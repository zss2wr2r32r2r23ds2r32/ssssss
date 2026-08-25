package com.sharded.core.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-player fake red glass block walls around spawn border segments — restored on clear. */
public final class CombatWallTracker {

    private static final int SEGMENT_RADIUS = 5;
    private static final int HEIGHT = 4;

    private final Map<UUID, Set<Location>> sent = new HashMap<>();

    public void showLocalSpawnWall(Player player, CuboidRegion spawn, Location near) {
        if (spawn == null || near.getWorld() == null) return;
        if (!spawn.world().equals(near.getWorld().getName())) return;
        clear(player);

        int px = near.getBlockX();
        int pz = near.getBlockZ();
        int baseY = near.getBlockY();
        Set<Location> blocks = new HashSet<>();

        for (int yOff = 0; yOff < HEIGHT; yOff++) {
            int y = baseY + yOff - 1;
            drawSegment(player, spawn, px, pz, y, blocks);
        }
        sent.put(player.getUniqueId(), blocks);
    }

    public void clear(Player player) {
        Set<Location> prev = sent.remove(player.getUniqueId());
        if (prev == null) return;
        for (Location loc : prev) {
            if (loc.getWorld() == null) continue;
            Block block = loc.getBlock();
            player.sendBlockChange(loc, block.getBlockData());
        }
    }

    public void clear(UUID uuid) {
        sent.remove(uuid);
    }

    private void drawSegment(Player player, CuboidRegion spawn, int px, int pz, int y, Set<Location> blocks) {
        int minX = spawn.minX();
        int maxX = spawn.maxX();
        int minZ = spawn.minZ();
        int maxZ = spawn.maxZ();

        if (Math.abs(pz - minZ) <= SEGMENT_RADIUS) {
            for (int x = px - SEGMENT_RADIUS; x <= px + SEGMENT_RADIUS; x++) {
                if (x < minX || x > maxX) continue;
                placeBlock(player, new Location(player.getWorld(), x, y, minZ), blocks);
            }
        }
        if (Math.abs(pz - maxZ) <= SEGMENT_RADIUS) {
            for (int x = px - SEGMENT_RADIUS; x <= px + SEGMENT_RADIUS; x++) {
                if (x < minX || x > maxX) continue;
                placeBlock(player, new Location(player.getWorld(), x, y, maxZ), blocks);
            }
        }
        if (Math.abs(px - minX) <= SEGMENT_RADIUS) {
            for (int z = pz - SEGMENT_RADIUS; z <= pz + SEGMENT_RADIUS; z++) {
                if (z < minZ || z > maxZ) continue;
                placeBlock(player, new Location(player.getWorld(), minX, y, z), blocks);
            }
        }
        if (Math.abs(px - maxX) <= SEGMENT_RADIUS) {
            for (int z = pz - SEGMENT_RADIUS; z <= pz + SEGMENT_RADIUS; z++) {
                if (z < minZ || z > maxZ) continue;
                placeBlock(player, new Location(player.getWorld(), maxX, y, z), blocks);
            }
        }
    }

    private void placeBlock(Player player, Location loc, Set<Location> blocks) {
        Block block = loc.getBlock();
        if (!block.getType().isAir() && !block.isReplaceable()) return;
        player.sendBlockChange(loc, Material.RED_STAINED_GLASS.createBlockData());
        blocks.add(loc.clone());
    }
}
