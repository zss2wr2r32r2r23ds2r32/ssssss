package com.sharded.core.modules.tokens;

import com.sharded.core.util.GuiFooters;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.PlaceholderUtil;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

final class TokenMethodsGuiHandler {
   private final TokensModule module;

   TokenMethodsGuiHandler(TokensModule module) {
      this.module = module;
   }

   void open(Player player) {
      String s = this.module.configString("gui.title", "&8Token Methods");
      int size = Math.max(9, Math.min(54, this.module.configInt("gui.size", 27)));
      if (size % 9 != 0) {
         size = 27;
      }
      TokenMethodsGuiHandler.Holder holder = new TokenMethodsGuiHandler.Holder();
      Inventory inventory = Bukkit.createInventory(holder, size, Text.c(s));
      holder.inventory = inventory;
      TrackedInventories.track(inventory, holder);
      this.fill(inventory);
      List<Entry<String, ConfigurationSection>> list = this.sortedMethods();
      String footer = this.module.configString("gui.click-footer", GuiFooters.view());
      int rank = 1;
      for (Entry<String, ConfigurationSection> entry : list) {
         ConfigurationSection section = entry.getValue();
         int slot = section.getInt("slot", -1);
         if (slot < 0 || slot >= inventory.getSize()) {
            continue;
         }
         Material material = Material.matchMaterial(section.getString("material", "PAPER"));
         if (material == null) {
            material = Material.PAPER;
         }
         List<String> lore = new ArrayList<>();
         for (String line : section.getStringList("lore")) {
            lore.add(
               PlaceholderUtil.apply(
                  player,
                  line.replace("%click%", footer)
                     .replace("%amount%", String.valueOf(section.getLong("amount", 50L)))
                     .replace("%rank%", String.valueOf(rank))
               )
            );
         }
         String name = PlaceholderUtil.apply(player, section.getString("name", entry.getKey()).replace("%rank%", String.valueOf(rank)));
         inventory.setItem(slot, new ItemBuilder(material).name(name).lore(lore).build());
         rank++;
      }
      player.openInventory(inventory);
   }

   void handleClick(Player player, int slot) {
      for (Entry<String, ConfigurationSection> entry : this.sortedMethods()) {
         ConfigurationSection section = entry.getValue();
         if (section.getInt("slot", -1) == slot) {
            String command = section.getString("command", "");
            if (command != null && !command.isBlank()) {
               player.performCommand(command.startsWith("/") ? command.substring(1) : command);
            }
            return;
         }
      }
   }

   private List<Entry<String, ConfigurationSection>> sortedMethods() {
      ConfigurationSection section = this.module.configSection("gui.methods");
      List<Entry<String, ConfigurationSection>> list = new ArrayList<>();
      if (section == null) {
         return list;
      }
      for (String key : section.getKeys(false)) {
         ConfigurationSection child = section.getConfigurationSection(key);
         if (child != null) {
            list.add(Map.entry(key, child));
         }
      }
      list.sort(Comparator.comparingInt(entry -> entry.getValue().getInt("order", 99)));
      return list;
   }

   private void fill(Inventory inventory) {
      Material material = Material.matchMaterial(this.module.configString("gui.filler-material", "GRAY_STAINED_GLASS_PANE"));
      if (material == null) {
         material = Material.GRAY_STAINED_GLASS_PANE;
      }
      ItemStack pane = new ItemBuilder(material).name(" ").build();
      for (int i = 0; i < inventory.getSize(); i++) {
         inventory.setItem(i, pane);
      }
   }

   static final class Holder implements InventoryHolder {
      Inventory inventory;

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
