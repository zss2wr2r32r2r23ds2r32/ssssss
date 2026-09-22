package com.sharded.core.modules.leaderboardboards;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

final class BoardDefinition {
   String id;
   String statistic;
   int entries = 5;
   boolean hologramEnabled = true;
   String title = "";
   List<String> lines = new ArrayList<>();
   String world;
   double x;
   double y;
   double z;
   float yaw;
   float pitch;
   Double headScale;
   Double headOffsetX;
   Double headOffsetY;
   Double headOffsetZ;
   Double lineHeight;

   String statisticKey() {
      return this.statistic == null ? this.id : this.statistic.toLowerCase(Locale.ROOT);
   }

   Location location() {
      if (this.world != null && !this.world.isBlank()) {
         World world = Bukkit.getWorld(this.world);
         return world == null ? null : new Location(world, this.x, this.y, this.z, this.yaw, this.pitch);
      } else {
         return null;
      }
   }

   boolean placed() {
      return this.world != null && !this.world.isBlank();
   }

   void setLocation(Location location) {
      this.world = location.getWorld() == null ? null : location.getWorld().getName();
      this.x = location.getX();
      this.y = location.getY();
      this.z = location.getZ();
      this.yaw = location.getYaw();
      this.pitch = location.getPitch();
   }

   void load(String id, ConfigurationSection section) {
      this.id = id;
      this.statistic = section.getString("statistic", id);
      this.entries = Math.max(1, Math.min(20, section.getInt("entries", 5)));
      ConfigurationSection configurationsection = section.getConfigurationSection("hologram");
      if (configurationsection == null) {
         configurationsection = section;
      }

      this.hologramEnabled = configurationsection.getBoolean("enabled", true);
      this.title = configurationsection.getString("title", "");
      this.lines = new ArrayList<>(configurationsection.getStringList("lines"));
      this.world = configurationsection.getString("world", section.getString("world"));
      this.x = configurationsection.getDouble("x", section.getDouble("x"));
      this.y = configurationsection.getDouble("y", section.getDouble("y"));
      this.z = configurationsection.getDouble("z", section.getDouble("z"));
      this.yaw = (float)configurationsection.getDouble("yaw", section.getDouble("yaw"));
      this.pitch = (float)configurationsection.getDouble("pitch", section.getDouble("pitch"));
      ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection("head");
      if (configurationsection1 != null) {
         if (configurationsection1.contains("scale")) {
            this.headScale = configurationsection1.getDouble("scale");
         }

         if (configurationsection1.contains("offset-x")) {
            this.headOffsetX = configurationsection1.getDouble("offset-x");
         }

         if (configurationsection1.contains("offset-y")) {
            this.headOffsetY = configurationsection1.getDouble("offset-y");
         }

         if (configurationsection1.contains("offset-z")) {
            this.headOffsetZ = configurationsection1.getDouble("offset-z");
         }
      }

      if (configurationsection.contains("line-height")) {
         this.lineHeight = configurationsection.getDouble("line-height");
      }
   }

   void save(ConfigurationSection section) {
      section.set("statistic", this.statistic);
      section.set("entries", this.entries);
      section.set("hologram.enabled", this.hologramEnabled);
      section.set("hologram.title", this.title);
      section.set("hologram.lines", this.lines);
      if (this.placed()) {
         section.set("hologram.world", this.world);
         section.set("hologram.x", this.x);
         section.set("hologram.y", this.y);
         section.set("hologram.z", this.z);
         section.set("hologram.yaw", this.yaw);
         section.set("hologram.pitch", this.pitch);
      }

      if (this.headScale != null) {
         section.set("hologram.head.scale", this.headScale);
      }

      if (this.headOffsetX != null) {
         section.set("hologram.head.offset-x", this.headOffsetX);
      }

      if (this.headOffsetY != null) {
         section.set("hologram.head.offset-y", this.headOffsetY);
      }

      if (this.headOffsetZ != null) {
         section.set("hologram.head.offset-z", this.headOffsetZ);
      }

      if (this.lineHeight != null) {
         section.set("hologram.line-height", this.lineHeight);
      }
   }
}
