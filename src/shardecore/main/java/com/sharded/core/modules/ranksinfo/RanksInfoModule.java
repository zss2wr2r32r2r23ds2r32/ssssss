package com.sharded.core.modules.ranksinfo;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.HeadUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class RanksInfoModule extends Module implements CommandExecutor {
   public RanksInfoModule(ShardedCore plugin) {
      super(plugin, "ranksinfo");
   }

   @Override
   protected void onEnable() {
      this.migrateScreenshotLayout();
      this.migrateColoredEggs();
      this.registerCommand("ranks", this);
      this.registerListener(this);
   }

   private void migrateScreenshotLayout() {
      if (this.config.getInt("config-version", 0) < 10
         || this.config.getInt("gui.rows", 3) != 4
         || this.config.getInt("ranks.ruby.slot", -1) != 11
         || this.config.getInt("ranks.booster.slot", -1) != 24) {
         this.config.set("gui.rows", 4);
         this.config.set("gui.filler", false);
         this.config.set("gui.filler-material", "AIR");
         this.config.set("gui.layout", "screenshot-v10");
         this.applySlot("ruby", 11, "PARROT_SPAWN_EGG", "&#FF0000&lRuby Rank");
         this.applySlot("sapphire", 12, "VEX_SPAWN_EGG", "&#55FFFF&lSapphire Rank");
         this.applySlot("onyx", 13, "BLAZE_SPAWN_EGG", "&#FFAA00&lOnyx Rank");
         this.applySlot("media", 15, "AXOLOTL_SPAWN_EGG", "&#FF55FF&lMedia Rank");
         this.applySlot("aether", 20, "ALLAY_SPAWN_EGG", "&#00AAAA&lAether Rank");
         this.applySlot("flawless", 21, "PHANTOM_SPAWN_EGG", "&#D5A6FF&lFlawless Rank");
         this.applySlot("booster", 24, "SHULKER_SPAWN_EGG", "&#AA00AA&lBooster Rank");
         this.writeConfig();
      }
   }

   private void migrateColoredEggs() {
      boolean flag = false;
      flag |= this.forceMaterial("ruby", "PARROT_SPAWN_EGG");
      flag |= this.forceMaterial("sapphire", "VEX_SPAWN_EGG");
      flag |= this.forceMaterial("onyx", "BLAZE_SPAWN_EGG");
      flag |= this.forceMaterial("media", "AXOLOTL_SPAWN_EGG");
      flag |= this.forceMaterial("aether", "ALLAY_SPAWN_EGG");
      flag |= this.forceMaterial("flawless", "PHANTOM_SPAWN_EGG");
      flag |= this.forceMaterial("booster", "SHULKER_SPAWN_EGG");
      flag |= this.forceSlot("ruby", 11);
      flag |= this.forceSlot("sapphire", 12);
      flag |= this.forceSlot("onyx", 13);
      flag |= this.forceSlot("media", 15);
      flag |= this.forceSlot("aether", 20);
      flag |= this.forceSlot("flawless", 21);
      flag |= this.forceSlot("booster", 24);
      if (this.config.getInt("gui.rows", 3) != 4) {
         this.config.set("gui.rows", 4);
         flag = true;
      }

      List<String> list = this.config.getStringList("ranks.media.lore");
      boolean flag1 = false;
      List<String> list1 = new ArrayList<>();

      for (String s : list) {
         if (s != null && s.toLowerCase(Locale.ROOT).contains("streamer")) {
            list1.add(s.replace("Streamer", "Creator").replace("streamer", "Creator").replace("&#FF0055", "&#8B1A1A"));
            flag1 = true;
         } else {
            list1.add(s);
         }
      }

      if (flag1) {
         this.config.set("ranks.media.lore", list1);
         flag = true;
      }

      if (flag || this.config.getInt("config-version", 0) < 10) {
         this.config.set("gui.layout", "screenshot-v10");
         this.config.set("config-version", 10);
         this.writeConfig();
      }
   }

   private boolean forceSlot(String id, int slot) {
      if (this.config.getInt("ranks." + id + ".slot", -1) == slot) {
         return false;
      } else {
         this.config.set("ranks." + id + ".slot", slot);
         return true;
      }
   }

   private boolean forceMaterial(String id, String material) {
      String s = this.config.getString("ranks." + id + ".material", "");
      if (material.equalsIgnoreCase(s)) {
         return false;
      } else {
         this.config.set("ranks." + id + ".material", material);
         return true;
      }
   }

   private void writeConfig() {
      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (Exception exception) {
      }
   }

   private void applySlot(String id, int slot, String material, String name) {
      if (this.config.getConfigurationSection("ranks." + id) != null) {
         this.config.set("ranks." + id + ".slot", slot);
         String s = this.config.getString("ranks." + id + ".material", "");
         if (s == null || s.isBlank() || s.toLowerCase(Locale.ROOT).contains("head") || s.startsWith("ey") || s.contains("basehead") || s.contains("texture")) {
            this.config.set("ranks." + id + ".material", material);
         }

         String s1 = this.config.getString("ranks." + id + ".name", "");
         if (s1 != null && s1.equals(s1.toUpperCase(Locale.ROOT))) {
            this.config.set("ranks." + id + ".name", name);
         }

         List<String> list = this.config.getStringList("ranks." + id + ".lore");
         if (!list.isEmpty()) {
            this.config.set("ranks." + id + ".lore", this.titleCaseLore(list));
         }
      }
   }

   private List<String> titleCaseLore(List<String> lore) {
      List<String> list = new ArrayList<>();

      for (String s : lore) {
         list.add(this.softenCaps(s));
      }

      return list;
   }

   private String softenCaps(String raw) {
      if (raw != null && !raw.isBlank()) {
         if (isAllCapsLetters(raw)) {
            return raw;
         }
         StringBuilder stringbuilder = new StringBuilder(raw.length());
         boolean flag = true;
         int i = 0;

         while (i < raw.length()) {
            char c0 = raw.charAt(i);
            if ((c0 == '&' || c0 == 167) && i + 1 < raw.length()) {
               char c1 = raw.charAt(i + 1);
               stringbuilder.append(c0).append(c1);
               i += 2;
               if (c1 == '#' && i + 6 <= raw.length()) {
                  stringbuilder.append(raw, i, i + 6);
                  i += 6;
               } else if (c1 == 'x' || c1 == 'X') {
                  for (int j = 0; j < 6 && i + 1 < raw.length(); j++) {
                     stringbuilder.append(raw.charAt(i)).append(raw.charAt(i + 1));
                     i += 2;
                  }
               }
            } else if (Character.isLetter(c0)) {
               stringbuilder.append(flag ? Character.toUpperCase(c0) : Character.toLowerCase(c0));
               flag = false;
               i++;
            } else {
               stringbuilder.append(c0);
               flag = c0 == ' ' || c0 == '|' || c0 == '-' || c0 == '/';
               i++;
            }
         }

         return stringbuilder.toString();
      } else {
         return raw;
      }
   }

   private static boolean isAllCapsLetters(String raw) {
      boolean any = false;
      int i = 0;
      while (i < raw.length()) {
         char c0 = raw.charAt(i);
         if ((c0 == '&' || c0 == 167) && i + 1 < raw.length()) {
            char c1 = raw.charAt(i + 1);
            i += 2;
            if (c1 == '#' && i + 6 <= raw.length()) {
               i += 6;
            } else if (c1 == 'x' || c1 == 'X') {
               i += 12;
            }
            continue;
         }
         if (Character.isLetter(c0)) {
            any = true;
            if (!Character.isUpperCase(c0)) {
               return false;
            }
         }
         i++;
      }
      return any;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         this.open(player);
         return true;
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private void open(Player player) {
      int i = Math.max(1, Math.min(6, this.config.getInt("gui.rows", 3)));
      RanksInfoModule.Holder ranksinfomodule$holder = new RanksInfoModule.Holder();
      Inventory inventory = Bukkit.createInventory(ranksinfomodule$holder, i * 9, Text.c(this.config.getString("gui.title", "Ranks")));
      ranksinfomodule$holder.inventory = inventory;
      TrackedInventories.track(inventory, ranksinfomodule$holder);
      if (this.config.getBoolean("gui.filler", false)) {
         Material material = Material.matchMaterial(this.config.getString("gui.filler-material", "GRAY_STAINED_GLASS_PANE").toUpperCase(Locale.ROOT));
         if (material == null || material == Material.AIR) {
            material = Material.GRAY_STAINED_GLASS_PANE;
         }

         ItemStack itemstack = new ItemBuilder(material).name(" ").build();

         for (int j = 0; j < inventory.getSize(); j++) {
            inventory.setItem(j, itemstack);
         }
      }

      ConfigurationSection configurationsection1 = this.config.getConfigurationSection("ranks");
      if (configurationsection1 != null) {
         for (String s1 : configurationsection1.getKeys(false)) {
            ConfigurationSection configurationsection = configurationsection1.getConfigurationSection(s1);
            if (configurationsection != null && configurationsection.getBoolean("enabled", true)) {
               int k = configurationsection.getInt("slot", 10);
               if (k >= 0 && k < inventory.getSize()) {
                  inventory.setItem(k, this.buildIcon(configurationsection, s1));
                  String s = configurationsection.getString("command", "");
                  if (s != null && !s.isBlank()) {
                     ranksinfomodule$holder.actions.put(k, "cmd:" + s);
                  }
               }
            }
         }
      }

      if (this.config.getBoolean("close.enabled", true)) {
         int l = this.config.getInt("close.slot", 22);
         if (l >= 0 && l < inventory.getSize()) {
            Material material1 = Material.matchMaterial(this.config.getString("close.material", "BARRIER").toUpperCase(Locale.ROOT));
            inventory.setItem(
               l,
               new ItemBuilder(material1 == null ? Material.BARRIER : material1)
                  .name(this.config.getString("close.name", "&#FF0000&lCLOSE"))
                  .lore(this.config.getStringList("close.lore"))
                  .build()
            );
            ranksinfomodule$holder.actions.put(l, "close");
         }
      }

      if (this.config.getBoolean("shop.enabled", false)) {
         int i1 = this.config.getInt("shop.slot", 4);
         if (i1 >= 0 && i1 < inventory.getSize()) {
            Material material2 = Material.matchMaterial(this.config.getString("shop.material", "EMERALD").toUpperCase(Locale.ROOT));
            inventory.setItem(
               i1,
               new ItemBuilder(material2 == null ? Material.EMERALD : material2)
                  .name(this.config.getString("shop.name", "&#26E07A&lWEBSTORE"))
                  .lore(this.config.getStringList("shop.lore"))
                  .build()
            );
            ranksinfomodule$holder.actions.put(i1, "shop");
         }
      }

      player.openInventory(inventory);
   }

   private ItemStack buildIcon(ConfigurationSection rank, String id) {
      String s = rank.getString("material", "NETHERITE_INGOT");
      ItemStack itemstack = null;
      if (s != null && !this.looksLikeHead(s)) {
         itemstack = HeadUtil.parse(s);
         if (itemstack != null && itemstack.getType() == Material.PLAYER_HEAD) {
            itemstack = null;
         }
      }

      if (itemstack == null) {
         Material material = Material.matchMaterial(s == null ? "" : s.toUpperCase(Locale.ROOT));
         if (material == null || material == Material.PLAYER_HEAD || material == Material.AIR) {
            material = Material.NETHERITE_INGOT;
         }

         itemstack = new ItemStack(material);
      }

      return new ItemBuilder(itemstack)
         .name(this.softenCaps(rank.getString("name", id)))
         .lore(this.titleCaseLore(rank.getStringList("lore")))
         .hideAll()
         .build();
   }

   private boolean looksLikeHead(String raw) {
      String s = raw.toLowerCase(Locale.ROOT);
      return s.contains("head") || s.contains("skull") || s.contains("basehead") || s.contains("texture") || s.startsWith("ey");
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         RanksInfoModule.Holder ranksinfomodule$holder = TrackedInventories.lookup(event.getView().getTopInventory(), RanksInfoModule.Holder.class);
         if (ranksinfomodule$holder != null) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
               String s = ranksinfomodule$holder.actions.get(event.getSlot());
               if (s != null) {
                  if (s.equals("close")) {
                     player.closeInventory();
                  } else if (s.equals("shop")) {
                     player.closeInventory();
                     String s2 = this.config.getString("shop.command", "store");
                     player.performCommand(s2.startsWith("/") ? s2.substring(1) : s2);
                  } else {
                     if (s.startsWith("cmd:")) {
                        player.closeInventory();
                        String s1 = s.substring(4).trim();
                        if (!s1.isBlank()) {
                           player.performCommand(s1.startsWith("/") ? s1.substring(1) : s1);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static final class Holder implements InventoryHolder {
      private final Map<Integer, String> actions = new LinkedHashMap<>();
      private Inventory inventory;

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
