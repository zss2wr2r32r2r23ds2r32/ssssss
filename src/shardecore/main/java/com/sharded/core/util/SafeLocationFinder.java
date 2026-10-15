package com.sharded.core.util;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

public final class SafeLocationFinder {
   private SafeLocationFinder() {
   }

   public static Location find(World world, ConfigurationSection settings) {
      if (world != null && settings != null) {
         int i = settings.getInt("min-radius", 100);
         int j = settings.getInt("max-radius", 500);
         int k = settings.getInt("center-x", 0);
         int l = settings.getInt("center-z", 0);
         int i1 = settings.getInt("max-attempts", 25);
         return find(world, k, l, i, j, i1);
      } else {
         return null;
      }
   }

   public static Location find(World world, int centerX, int centerZ, int minRadius, int maxRadius, int attempts) {
      if (world == null) {
         return null;
      } else {
         ThreadLocalRandom threadlocalrandom = ThreadLocalRandom.current();

         for (int i = 0; i < attempts; i++) {
            int j = threadlocalrandom.nextInt(minRadius, Math.max(minRadius + 1, maxRadius));
            double d0 = threadlocalrandom.nextDouble() * Math.PI * 2.0;
            int k = centerX + (int)(Math.cos(d0) * (double)j);
            int l = centerZ + (int)(Math.sin(d0) * (double)j);
            int i1 = world.getHighestBlockYAt(k, l);
            if (i1 > world.getMinHeight()) {
               Block block = world.getBlockAt(k, i1, l);
               Material material = block.getType();
               if (material != Material.LAVA
                  && material != Material.WATER
                  && material != Material.CACTUS
                  && material != Material.MAGMA_BLOCK
                  && material != Material.POWDER_SNOW
                  && !material.isAir()) {
                  return new Location(world, (double)k + 0.5, (double)i1 + 1.0, (double)l + 0.5);
               }
            }
         }

         return null;
      }
   }
}
