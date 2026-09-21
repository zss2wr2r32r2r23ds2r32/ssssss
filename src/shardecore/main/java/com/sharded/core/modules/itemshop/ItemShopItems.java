package com.sharded.core.modules.itemshop;

import com.sharded.core.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ItemShopItems {
   private ItemShopItems() {
   }

   public static String clean(String input) {
      if (input == null || input.isEmpty()) {
         return "";
      }
      return input.replaceAll("(?i)</?[a-z][^>]*>", "").trim();
   }

   public static List<String> cleanLines(List<String> lines) {
      List<String> out = new ArrayList<>();
      for (String line : lines) {
         String cleaned = clean(line);
         if (!cleaned.isEmpty()) {
            out.add(cleaned);
         }
      }
      return out;
   }

   public static void applyName(ItemMeta meta, String name) {
      if (meta == null) {
         return;
      }
      meta.displayName(Text.c(name == null || name.isEmpty() ? " " : name));
      meta.itemName(null);
   }

   public static ItemStack fromSection(ConfigurationSection section, Material fallback, String name, List<String> lore) {
      Material material = ItemShopTypes.material(section, "material", fallback);
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         applyName(meta, name);
         List<Component> components = new ArrayList<>();
         for (String line : lore) {
            components.add(Text.c(line));
         }
         meta.lore(components);
         meta.addItemFlags(ItemFlag.values());
         item.setItemMeta(meta);
      }
      return item;
   }

   public static ItemStack cosmeticIcon(ItemShopCatalog catalog, ItemShopTypes.Cosmetic cosmetic, HatDisplayManager hats, boolean glow) {
      ItemStack item = hats.stack(cosmetic);
      if ("tag".equals(cosmetic.type()) && (item.getType().isAir() || item.getType() == Material.PAPER)) {
         item = new ItemStack(Material.NAME_TAG);
      }
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         applyName(meta, cosmetic.displayName());
         if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
         }
         meta.addItemFlags(ItemFlag.values());
         item.setItemMeta(meta);
      }
      return item;
   }

   public static void lore(ItemStack item, List<String> lines) {
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return;
      }
      List<Component> components = new ArrayList<>();
      for (String line : lines) {
         components.add(Text.c(line));
      }
      meta.lore(components);
      item.setItemMeta(meta);
   }

   public static void mark(ItemStack item, NamespacedKey key, String value) {
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
         item.setItemMeta(meta);
      }
   }

   public static String read(ItemStack item, NamespacedKey key) {
      if (item == null || !item.hasItemMeta()) {
         return null;
      }
      return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
   }

   public static String apply(String template, String... pairs) {
      String out = template == null ? "" : template;
      for (int i = 0; i + 1 < pairs.length; i += 2) {
         out = out.replace(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]);
      }
      return out;
   }

   public static void play(Player player, ConfigurationSection section) {
      if (player == null || section == null || !section.getBoolean("enabled", true)) {
         return;
      }
      String sound = section.getString("sound", "ui.button.click");
      float volume = (float) section.getDouble("volume", 1.0);
      float pitch = (float) section.getDouble("pitch", 1.0);
      player.playSound(player.getLocation(), sound.toLowerCase(Locale.ROOT), volume, pitch);
   }
}
