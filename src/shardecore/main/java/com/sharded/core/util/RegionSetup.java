package com.sharded.core.util;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class RegionSetup {
   private final Map<UUID, Location> pos1 = new HashMap<>();
   private final Map<UUID, Location> pos2 = new HashMap<>();

   public void setPos1(Player player, Location loc) {
      this.pos1.put(player.getUniqueId(), loc.clone());
   }

   public void setPos2(Player player, Location loc) {
      this.pos2.put(player.getUniqueId(), loc.clone());
   }

   public Location pos1(Player player) {
      return this.pos1.get(player.getUniqueId());
   }

   public Location pos2(Player player) {
      return this.pos2.get(player.getUniqueId());
   }

   public CuboidRegion build(Player player) {
      Location location = this.pos1(player);
      Location location1 = this.pos2(player);
      if (location != null && location1 != null && location.getWorld() != null && location1.getWorld() != null) {
         return !location.getWorld().equals(location1.getWorld())
            ? null
            : new CuboidRegion(
               location.getWorld().getName(),
               location.getBlockX(),
               location.getBlockY(),
               location.getBlockZ(),
               location1.getBlockX(),
               location1.getBlockY(),
               location1.getBlockZ()
            );
      } else {
         return null;
      }
   }

   public void saveRegion(YamlConfiguration config, String path, CuboidRegion region, File file) throws Exception {
      config.createSection(path).set("world", null);
      region.write(config.getConfigurationSection(path));
      config.save(file);
   }
}
