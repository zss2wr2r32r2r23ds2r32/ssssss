package com.sharded.core.gui;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.HeadUtil;
import com.sharded.core.util.ItemBuilder;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class GuiNavigation {
   private YamlConfiguration config;

   public GuiNavigation(ShardedCore plugin) {
      this.reload(plugin);
   }

   public void reload(ShardedCore plugin) {
      File file1 = new File(plugin.getDataFolder(), "gui-navigation.yml");
      ConfigSync.sync(plugin, file1, "gui-navigation.yml");
      this.config = YamlConfiguration.loadConfiguration(file1);
   }

   public ItemStack build(String type) {
      return this.build(type, null);
   }

   public ItemStack build(String type, ConfigurationSection override) {
      ConfigurationSection configurationsection = this.section(type);
      if (configurationsection == null && override == null) {
         return new ItemBuilder(Material.BARRIER).name("&cMissing nav: " + type).build();
      } else {
         String s = pickString(override, configurationsection, "material", "STONE");
         ItemStack itemstack = HeadUtil.parse(s);
         if (itemstack == null) {
            Material material = Material.matchMaterial(s.toUpperCase(Locale.ROOT));
            itemstack = new ItemStack(material == null ? Material.STONE : material);
         }

         String s1 = pickString(override, configurationsection, "display_name", pickString(override, configurationsection, "name", "&f" + type));
         List<String> list = pickLore(override, configurationsection);
         return new ItemBuilder(itemstack).name(s1).lore(list).hideAll().build();
      }
   }

   public ItemStack filler(String kind) {
      ConfigurationSection section = this.config == null ? null : this.config.getConfigurationSection("fillers." + kind);
      String materialName = section != null ? section.getString("material", "GRAY_STAINED_GLASS_PANE") : "GRAY_STAINED_GLASS_PANE";
      Material material = Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
      if (material == null || !material.isItem()) {
         material = "border".equals(kind) || "dark".equals(kind) ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
      }
      String name = section != null ? section.getString("name", " ") : " ";
      List<String> lore = section != null ? section.getStringList("lore") : List.of();
      return new ItemBuilder(material).name(name == null || name.isBlank() ? " " : name).lore(lore).hideAll().build();
   }

   public YamlConfiguration yaml() {
      return this.config;
   }

   public String sound(String key, String def) {
      if (this.config == null) {
         return def;
      }
      String value = this.config.getString("sounds." + key);
      return value == null || value.isBlank() ? def : value;
   }

   public float soundVolume() {
      return this.config == null ? 1.0F : (float) this.config.getDouble("sounds.volume", 1.0D);
   }

   public float soundPitch() {
      return this.config == null ? 1.0F : (float) this.config.getDouble("sounds.pitch", 1.0D);
   }

   public String displayName(String type, ConfigurationSection override) {
      ConfigurationSection configurationsection = this.section(type);
      return pickString(override, configurationsection, "display_name", pickString(override, configurationsection, "name", "&f" + type));
   }

   public List<String> lore(String type, ConfigurationSection override) {
      return pickLore(override, this.section(type));
   }

   public Material material(String type, ConfigurationSection override) {
      ConfigurationSection configurationsection = this.section(type);
      Material material = Material.matchMaterial(pickString(override, configurationsection, "material", "STONE").toUpperCase(Locale.ROOT));
      return material == null ? Material.STONE : material;
   }

   public ConfigurationSection section(String type) {
      return this.config == null ? null : this.config.getConfigurationSection(type.toLowerCase(Locale.ROOT));
   }

   private static String pickString(ConfigurationSection override, ConfigurationSection base, String key, String def) {
      if (override != null && override.contains(key)) {
         return override.getString(key, def);
      } else {
         return base != null && base.contains(key) ? base.getString(key, def) : def;
      }
   }

   private static List<String> pickLore(ConfigurationSection override, ConfigurationSection base) {
      if (override != null && !override.getStringList("lore").isEmpty()) {
         return new ArrayList<>(override.getStringList("lore"));
      } else {
         return (List<String>)(base != null && !base.getStringList("lore").isEmpty() ? new ArrayList<>(base.getStringList("lore")) : List.of());
      }
   }

   public static String resolveNavType(String itemKey, ConfigurationSection item) {
      if (item != null && item.contains("nav")) {
         return item.getString("nav", "").toLowerCase(Locale.ROOT);
      } else if (itemKey == null) {
         return null;
      } else {
         String s = itemKey.toLowerCase(Locale.ROOT);

         return switch (s) {
            case "back_button", "back" -> "back";
            case "close_menu", "close", "cancel" -> "close";
            case "previous_button", "previous" -> "previous";
            case "next_button", "next" -> "next";
            default -> null;
         };
      }
   }
}
