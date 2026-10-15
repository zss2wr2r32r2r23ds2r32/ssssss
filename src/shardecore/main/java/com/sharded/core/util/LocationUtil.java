package com.sharded.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public final class LocationUtil {
   private LocationUtil() {
   }

   public static void write(ConfigurationSection section, Location location) {
      if (section != null && location != null && location.getWorld() != null) {
         section.set("world", location.getWorld().getName());
         section.set("x", location.getX());
         section.set("y", location.getY());
         section.set("z", location.getZ());
         section.set("yaw", location.getYaw());
         section.set("pitch", location.getPitch());
      }
   }

   public static Location read(ConfigurationSection section) {
      if (section != null && section.isString("world")) {
         World world = Bukkit.getWorld(section.getString("world", ""));
         return world == null
            ? null
            : new Location(
               world,
               section.getDouble("x"),
               section.getDouble("y"),
               section.getDouble("z"),
               (float)section.getDouble("yaw"),
               (float)section.getDouble("pitch")
            );
      } else {
         return null;
      }
   }
}
