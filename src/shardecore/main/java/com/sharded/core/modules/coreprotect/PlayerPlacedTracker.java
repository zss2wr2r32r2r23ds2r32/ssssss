package com.sharded.core.modules.coreprotect;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

final class PlayerPlacedTracker {
   private final File file;
   private final Set<String> blocks = new HashSet<>();

   PlayerPlacedTracker(File moduleFolder) {
      this.file = new File(moduleFolder, "player-placed.yml");
      this.load();
   }

   void mark(Location location) {
      this.blocks.add(key(location));
   }

   void unmark(Location location) {
      this.blocks.remove(key(location));
   }

   boolean isPlaced(Location location) {
      return this.blocks.contains(key(location));
   }

   void clearSide(String sideId) {
      String s = sideId.toLowerCase() + ":";
      this.blocks.removeIf(key -> key.startsWith(s));
   }

   void clearAll() {
      this.blocks.clear();
   }

   void save() {
      YamlConfiguration yamlconfiguration = new YamlConfiguration();
      yamlconfiguration.set("blocks", List.copyOf(this.blocks));

      try {
         yamlconfiguration.save(this.file);
      } catch (IOException ioexception) {
      }
   }

   private void load() {
      if (this.file.exists()) {
         YamlConfiguration yamlconfiguration = YamlConfiguration.loadConfiguration(this.file);
         this.blocks.clear();

         for (String s : yamlconfiguration.getStringList("blocks")) {
            if (s != null && !s.isBlank()) {
               this.blocks.add(s);
            }
         }
      }
   }

   static String key(Location location) {
      return location.getWorld() == null
         ? "?"
         : location.getWorld().getName().toLowerCase() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
   }
}
