package com.sharded.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class CuboidRegion {
   private final String world;
   private final int minX;
   private final int minY;
   private final int minZ;
   private final int maxX;
   private final int maxY;
   private final int maxZ;

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
      return section != null && section.isString("world")
         ? new CuboidRegion(
            section.getString("world"),
            section.getInt("x1"),
            section.getInt("y1"),
            section.getInt("z1"),
            section.getInt("x2"),
            section.getInt("y2"),
            section.getInt("z2")
         )
         : null;
   }

   public void write(ConfigurationSection section) {
      section.set("world", this.world);
      section.set("x1", this.minX);
      section.set("y1", this.minY);
      section.set("z1", this.minZ);
      section.set("x2", this.maxX);
      section.set("y2", this.maxY);
      section.set("z2", this.maxZ);
   }

   public String world() {
      return this.world;
   }

   public World bukkitWorld() {
      return Bukkit.getWorld(this.world);
   }

   public boolean contains(Location loc) {
      if (loc != null && loc.getWorld() != null) {
         if (!loc.getWorld().getName().equals(this.world)) {
            return false;
         } else {
            int i = loc.getBlockX();
            int j = loc.getBlockY();
            int k = loc.getBlockZ();
            return i >= this.minX && i <= this.maxX && j >= this.minY && j <= this.maxY && k >= this.minZ && k <= this.maxZ;
         }
      } else {
         return false;
      }
   }

   public boolean contains(Player player) {
      return this.contains(player.getLocation());
   }

   public int volume() {
      return (this.maxX - this.minX + 1) * (this.maxY - this.minY + 1) * (this.maxZ - this.minZ + 1);
   }

   public Location center() {
      World world = this.bukkitWorld();
      if (world == null) {
         return null;
      }
      double x = (double)(this.minX + this.maxX + 1) / 2.0;
      double y = (double)this.minY + 1.0;
      double z = (double)(this.minZ + this.maxZ + 1) / 2.0;
      return new Location(world, x, y, z);
   }

   public int minX() {
      return this.minX;
   }

   public int minY() {
      return this.minY;
   }

   public int minZ() {
      return this.minZ;
   }

   public int maxX() {
      return this.maxX;
   }

   public int maxY() {
      return this.maxY;
   }

   public int maxZ() {
      return this.maxZ;
   }
}
