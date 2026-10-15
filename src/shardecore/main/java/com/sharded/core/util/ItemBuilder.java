package com.sharded.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemBuilder {
   private final ItemStack item;

   public ItemBuilder(Material material) {
      this.item = new ItemStack(material);
   }

   public ItemBuilder(ItemStack item) {
      this.item = item.clone();
   }

   public ItemBuilder name(String legacyName) {
      return this.edit(meta -> {
         meta.displayName(Text.c(legacyName));
         meta.itemName(null);
      });
   }

   public ItemBuilder lore(List<String> lines) {
      return this.edit(meta -> {
         List<Component> list = new ArrayList<>();

         for (String s : lines) {
            list.add(Text.c(s));
         }

         meta.lore(list);
      });
   }

   public ItemBuilder lore(String... lines) {
      return this.lore(List.of(lines));
   }

   public ItemBuilder glow(boolean glow) {
      return this.edit(meta -> {
         if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
         } else {
            meta.removeEnchant(Enchantment.UNBREAKING);
         }
      });
   }

   public ItemBuilder hideAll() {
      return this.edit(meta -> meta.addItemFlags(ItemFlag.values()));
   }

   public ItemBuilder edit(Consumer<ItemMeta> consumer) {
      ItemMeta itemmeta = this.item.getItemMeta();
      if (itemmeta != null) {
         consumer.accept(itemmeta);
         this.item.setItemMeta(itemmeta);
      }

      return this;
   }

   public ItemStack build() {
      return this.item;
   }
}
