package com.sharded.core.modules.itemedit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

final class ItemLibrary {
   static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");
   private final File file;
   private YamlConfiguration yaml = new YamlConfiguration();

   ItemLibrary(File file) {
      this.file = file;
   }

   void load() {
      this.yaml = this.file.isFile() ? YamlConfiguration.loadConfiguration(this.file) : new YamlConfiguration();
   }

   void saveQuietly() {
      try {
         File file1 = this.file.getParentFile();
         if (file1 != null) {
            file1.mkdirs();
         }

         this.yaml.save(this.file);
      } catch (IOException ioexception) {
      }
   }

   static boolean validId(String id) {
      return id != null && ID.matcher(id).matches();
   }

   static String normalizeId(String raw) {
      return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
   }

   int size() {
      ConfigurationSection configurationsection = this.yaml.getConfigurationSection("items");
      return configurationsection == null ? 0 : configurationsection.getKeys(false).size();
   }

   List<String> ids() {
      ConfigurationSection configurationsection = this.yaml.getConfigurationSection("items");
      if (configurationsection == null) {
         return List.of();
      } else {
         List<String> list = new ArrayList<>(configurationsection.getKeys(false));
         Collections.sort(list);
         return list;
      }
   }

   ItemStack get(String id) {
      ItemStack itemstack = this.yaml.getItemStack("items." + id + ".item");
      return itemstack == null ? null : itemstack.clone();
   }

   boolean contains(String id) {
      return this.yaml.getItemStack("items." + id + ".item") != null;
   }

   void put(String id, ItemStack item) {
      this.yaml.set("items." + id + ".item", item.clone());
      this.saveQuietly();
   }

   void delete(String id) {
      this.yaml.set("items." + id, null);
      this.saveQuietly();
   }

   void setPrice(String id, long tokens) {
      this.yaml.set("items." + id + ".price", tokens);
      this.saveQuietly();
   }

   long price(String id) {
      return this.yaml.getLong("items." + id + ".price", -1L);
   }
}
