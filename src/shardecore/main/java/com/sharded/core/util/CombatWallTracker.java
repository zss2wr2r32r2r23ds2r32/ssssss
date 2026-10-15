package com.sharded.core.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class CombatWallTracker {
   private static final int SEGMENT_RADIUS = 5;
   private static final int HEIGHT = 4;
   private final Map<UUID, Set<Location>> sent = new HashMap<>();

   public void showLocalSpawnWall(Player player, CuboidRegion spawn, Location near) {
      if (spawn != null && near.getWorld() != null) {
         if (spawn.world().equals(near.getWorld().getName())) {
            this.clear(player);
            int i = near.getBlockX();
            int j = near.getBlockZ();
            int k = near.getBlockY();
            Set<Location> set = new HashSet<>();

            for (int l = 0; l < 4; l++) {
               int i1 = k + l - 1;
               this.drawSegment(player, spawn, i, j, i1, set);
            }

            this.sent.put(player.getUniqueId(), set);
         }
      }
   }

   public void clear(Player player) {
      Set<Location> set = this.sent.remove(player.getUniqueId());
      if (set != null) {
         for (Location location : set) {
            if (location.getWorld() != null) {
               Block block = location.getBlock();
               player.sendBlockChange(location, block.getBlockData());
            }
         }
      }
   }

   public void clear(UUID uuid) {
      this.sent.remove(uuid);
   }

   private void drawSegment(Player player, CuboidRegion spawn, int px, int pz, int y, Set<Location> blocks) {
      int i = spawn.minX();
      int j = spawn.maxX();
      int k = spawn.minZ();
      int l = spawn.maxZ();
      if (Math.abs(pz - k) <= 5) {
         for (int i1 = px - 5; i1 <= px + 5; i1++) {
            if (i1 >= i && i1 <= j) {
               this.placeBlock(player, new Location(player.getWorld(), (double)i1, (double)y, (double)k), blocks);
            }
         }
      }

      if (Math.abs(pz - l) <= 5) {
         for (int j1 = px - 5; j1 <= px + 5; j1++) {
            if (j1 >= i && j1 <= j) {
               this.placeBlock(player, new Location(player.getWorld(), (double)j1, (double)y, (double)l), blocks);
            }
         }
      }

      if (Math.abs(px - i) <= 5) {
         for (int k1 = pz - 5; k1 <= pz + 5; k1++) {
            if (k1 >= k && k1 <= l) {
               this.placeBlock(player, new Location(player.getWorld(), (double)i, (double)y, (double)k1), blocks);
            }
         }
      }

      if (Math.abs(px - j) <= 5) {
         for (int l1 = pz - 5; l1 <= pz + 5; l1++) {
            if (l1 >= k && l1 <= l) {
               this.placeBlock(player, new Location(player.getWorld(), (double)j, (double)y, (double)l1), blocks);
            }
         }
      }
   }

   private void placeBlock(Player player, Location loc, Set<Location> blocks) {
      Block block = loc.getBlock();
      if (block.getType().isAir() || block.isReplaceable()) {
         player.sendBlockChange(loc, Material.RED_STAINED_GLASS.createBlockData());
         blocks.add(loc.clone());
      }
   }
}
