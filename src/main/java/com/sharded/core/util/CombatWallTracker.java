package com.sharded.core.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.GlassPane;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-player fake red glass walls around spawn border segments — restored on clear. */
public final class CombatWallTracker {

    private static final int SEGMENT_RADIUS = 4;
    private static final int HEIGHT = 3;

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

        // North/south edges near player
        if (Math.abs(pz - minZ) <= SEGMENT_RADIUS) {
            for (int x = px - SEGMENT_RADIUS; x <= px + SEGMENT_RADIUS; x++) {
                if (x < minX || x > maxX) continue;
                placePane(player, new Location(player.getWorld(), x, y, minZ), true, false, blocks);
            }
        }
        if (Math.abs(pz - maxZ) <= SEGMENT_RADIUS) {
            for (int x = px - SEGMENT_RADIUS; x <= px + SEGMENT_RADIUS; x++) {
                if (x < minX || x > maxX) continue;
                placePane(player, new Location(player.getWorld(), x, y, maxZ), true, false, blocks);
            }
        }
        // East/west edges near player
        if (Math.abs(px - minX) <= SEGMENT_RADIUS) {
            for (int z = pz - SEGMENT_RADIUS; z <= pz + SEGMENT_RADIUS; z++) {
                if (z < minZ || z > maxZ) continue;
                placePane(player, new Location(player.getWorld(), minX, y, z), false, true, blocks);
            }
        }
        if (Math.abs(px - maxX) <= SEGMENT_RADIUS) {
            for (int z = pz - SEGMENT_RADIUS; z <= pz + SEGMENT_RADIUS; z++) {
                if (z < minZ || z > maxZ) continue;
                placePane(player, new Location(player.getWorld(), maxX, y, z), false, true, blocks);
            }
        }
    }

    private void placePane(Player player, Location loc, boolean nsWall, boolean ewWall,
                           Set<Location> blocks) {
        BlockData data = Material.RED_STAINED_GLASS_PANE.createBlockData();
        if (data instanceof GlassPane pane) {
            pane.setFace(org.bukkit.block.BlockFace.NORTH, nsWall);
            pane.setFace(org.bukkit.block.BlockFace.SOUTH, nsWall);
            pane.setFace(org.bukkit.block.BlockFace.EAST, ewWall);
            pane.setFace(org.bukkit.block.BlockFace.WEST, ewWall);
            data = pane;
        }
        player.sendBlockChange(loc, data);
        blocks.add(loc.clone());
    }
}
