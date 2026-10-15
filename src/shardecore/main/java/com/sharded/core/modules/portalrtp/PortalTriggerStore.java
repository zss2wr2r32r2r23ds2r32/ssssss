package com.sharded.core.modules.portalrtp;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PortalTriggerStore {
   private final ShardedCore plugin;
   private final File file;
   private final YamlConfiguration yaml;
   private final Set<String> triggers = new HashSet<>();

   public PortalTriggerStore(ShardedCore plugin, File folder) {
      this.plugin = plugin;
      this.file = new File(folder, "portal-triggers.yml");
      this.yaml = YamlConfiguration.loadConfiguration(this.file);
      this.reload();
   }

   public void reload() {
      this.triggers.clear();
      List<String> list = this.yaml.getStringList("triggers");
      if (list != null) {
         this.triggers.addAll(list);
      }
   }

   public boolean isTrigger(Location location) {
      if (location.getWorld() == null) {
         return false;
      } else {
         return this.triggers.isEmpty() ? false : this.triggers.contains(this.key(location));
      }
   }

   public void add(Location location) {
      this.triggers.add(this.key(location));
      this.yaml.set("triggers", List.copyOf(this.triggers));
      this.save();
   }

   public void remove(Location location) {
      this.triggers.remove(this.key(location));
      this.yaml.set("triggers", List.copyOf(this.triggers));
      this.save();
   }

   public int count() {
      return this.triggers.size();
   }

   private String key(Location loc) {
      if (loc.getWorld() == null) {
         return "?";
      } else {
         loc = loc.getBlock().getLocation();
         return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
      }
   }

   private void save() {
      try {
         this.yaml.save(this.file);
      } catch (IOException ioexception) {
         this.plugin.getLogger().warning("Could not save portal-triggers.yml: " + ioexception.getMessage());
      }
   }
}
