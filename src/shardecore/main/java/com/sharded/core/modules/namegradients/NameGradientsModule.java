package com.sharded.core.modules.namegradients;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.BundleColorUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class NameGradientsModule extends Module implements CommandExecutor, TabCompleter {
   private final Map<String, NameGradientsModule.GradientDef> gradients = new LinkedHashMap<>();

   public NameGradientsModule(ShardedCore plugin) {
      super(plugin, "namegradients");
   }

   @Override
   protected void onEnable() {
      this.reloadGradients();
      this.registerCommand("namegradients", this);
      this.registerCommand("namegradient", this);
      this.registerListener(this);
   }

   private void reloadGradients() {
      this.loadConfigs();
      this.gradients.clear();
      ConfigurationSection configurationsection = this.config.getConfigurationSection("gradients");
      if (configurationsection != null) {
         int i = 10;

         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            if (configurationsection1 != null) {
               String s1 = configurationsection1.getString("hex", "FFFFFF");
               String s2 = configurationsection1.getString("hex2", s1);
               String s3 = configurationsection1.getString("hex3", null);
               String s4 = configurationsection1.getString("type", s3 != null && !s3.isBlank() ? "gradient" : "gradient");
               String s5;
               if ("solid".equalsIgnoreCase(s4) && (s3 == null || s3.isBlank()) && s1.equals(s2)) {
                  s5 = "&#" + s1.replace("#", "");
               } else if (s3 != null && !s3.isBlank()) {
                  s5 = "#" + s1.replace("#", "") + " #" + s2.replace("#", "") + " #" + s3.replace("#", "");
               } else {
                  s5 = "#" + s1.replace("#", "") + " #" + s2.replace("#", "");
               }

               int j;
               if (configurationsection1.contains("slot")) {
                  j = configurationsection1.getInt("slot");
               } else {
                  j = i++;
                  if (i == 17) {
                     i = 19;
                  }

                  if (i == 22) {
                     i = 23;
                  }
               }

               Color color = BundleColorUtil.fromHex(s1.replace("#", ""));
               if (color == null) {
                  color = Color.fromRGB(163, 112, 238);
               }

               this.gradients
                  .put(
                     s.toLowerCase(Locale.ROOT),
                     new NameGradientsModule.GradientDef(
                        s.toLowerCase(Locale.ROOT),
                        configurationsection1.getString("name", s.replace('_', ' ').toUpperCase(Locale.ROOT)),
                        s5,
                        s1.replace("#", ""),
                        s2.replace("#", ""),
                        s3 == null ? null : s3.replace("#", ""),
                        configurationsection1.getString("permission", "sharded.namegradient." + s.toLowerCase(Locale.ROOT)),
                        j,
                        color
                     )
                  );
            }
         }
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.namegradient.use") && !player.hasPermission("sharded.namecolor.use") && !player.isOp()) {
            this.msg(player, "no-permission");
            return true;
         } else if (args.length == 0) {
            try {
               this.openMenu(player);
            } catch (Throwable throwable) {
               this.plugin.getLogger().severe("[namegradients] Failed to open GUI for " + player.getName() + ": " + throwable.getMessage());
               throwable.printStackTrace();
               this.msg(player, "open-error");
            }

            return true;
         } else {
            String s = args[0].toLowerCase(Locale.ROOT);
            if (s.equals("set") && args.length >= 2) {
               return this.equip(player, args[1].toLowerCase(Locale.ROOT));
            } else if (!s.equals("clear") && !s.equals("reset")) {
               if (this.gradients.containsKey(s)) {
                  return this.equip(player, s);
               } else {
                  this.msg(player, "usage");
                  return true;
               }
            } else {
               if (this.plugin.cosmetics() != null) {
                  this.plugin.cosmetics().clearNameColor(player);
               }

               this.msg(player, "cleared");
               return true;
            }
         }
      } else {
         this.msg(sender, "players-only");
         return true;
      }
   }

   public void openOwned(Player player) {
      this.openOwned(player, 0);
   }

   private void openOwned(Player player, int page) {
      List<NameGradientsModule.GradientDef> list = new ArrayList<>();
      for (NameGradientsModule.GradientDef gradient : this.gradients.values()) {
         if (this.owns(player, gradient)) {
            list.add(gradient);
         }
      }
      int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
      int pages = Math.max(1, (list.size() + slots.length - 1) / Math.max(1, slots.length));
      page = Math.max(0, Math.min(page, pages - 1));
      NameGradientsModule.Holder holder = new NameGradientsModule.Holder();
      holder.owned = true;
      holder.page = page;
      Inventory inventory = Bukkit.createInventory(holder, 54, Text.c(this.config.getString("gui.owned-title", "&#00E0FF&lYour Name Gradients")));
      holder.inventory = inventory;
      TrackedInventories.track(inventory, holder);
      ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
      for (int i = 0; i < inventory.getSize(); i++) {
         inventory.setItem(i, filler);
      }
      int start = page * slots.length;
      for (int i = 0; i < slots.length && start + i < list.size(); i++) {
         NameGradientsModule.GradientDef gradient = list.get(start + i);
         inventory.setItem(slots[i], this.item(player, gradient));
         holder.actions.put(slots[i], "equip:" + gradient.id);
      }
      if (list.isEmpty()) {
         inventory.setItem(
            22,
            new ItemBuilder(Material.PAPER)
               .name("&#00E0FF&lNO NAME GRADIENTS")
               .lore("&8Description", "", "&#00E0FF| &fYou do not own any name gradients yet.")
               .build()
         );
      }
      if (this.config.getBoolean("clear.enabled", true)) {
         inventory.setItem(
            49,
            new ItemBuilder(this.material(this.config.getString("clear.material"), Material.BARRIER))
               .name(this.config.getString("clear.name", "&#FF0000&lCLEAR NAME GRADIENT"))
               .lore(this.config.getStringList("clear.lore"))
               .build()
         );
         holder.actions.put(49, "clear");
      }
      if (page > 0) {
         inventory.setItem(45, new ItemBuilder(Material.ARROW).name("&#ff0000&lPREVIOUS PAGE").build());
         holder.actions.put(45, "page:" + (page - 1));
      }
      if (page < pages - 1) {
         inventory.setItem(53, new ItemBuilder(Material.ARROW).name("&#80ff00&lNEXT PAGE").build());
         holder.actions.put(53, "page:" + (page + 1));
      }
      this.play(player, "open");
      player.openInventory(inventory);
   }

   private void openMenu(Player player) {
      int i = Math.max(1, Math.min(6, this.config.getInt("gui.rows", 4)));
      NameGradientsModule.Holder namegradientsmodule$holder = new NameGradientsModule.Holder();
      Inventory inventory = Bukkit.createInventory(namegradientsmodule$holder, i * 9, Text.c(this.config.getString("gui.title", "Name Gradients")));
      namegradientsmodule$holder.inventory = inventory;
      TrackedInventories.track(inventory, namegradientsmodule$holder);
      Material material = this.material(this.config.getString("filler.material"), Material.GRAY_STAINED_GLASS_PANE);
      ItemStack itemstack = new ItemBuilder(material).name(this.config.getString("filler.name", " ")).build();

      for (int j = 0; j < inventory.getSize(); j++) {
         inventory.setItem(j, itemstack);
      }

      for (NameGradientsModule.GradientDef namegradientsmodule$gradientdef : this.gradients.values()) {
         if (namegradientsmodule$gradientdef.slot >= 0 && namegradientsmodule$gradientdef.slot < inventory.getSize()) {
            try {
               inventory.setItem(namegradientsmodule$gradientdef.slot, this.item(player, namegradientsmodule$gradientdef));
               namegradientsmodule$holder.actions.put(namegradientsmodule$gradientdef.slot, "equip:" + namegradientsmodule$gradientdef.id);
            } catch (Throwable throwable) {
               this.plugin.getLogger().warning("[namegradients] Failed to render " + namegradientsmodule$gradientdef.id + ": " + throwable.getMessage());
            }
         }
      }

      if (this.config.getBoolean("clear.enabled", true)) {
         int k = this.config.getInt("clear.slot", 4);
         if (k >= 0 && k < inventory.getSize()) {
            inventory.setItem(
               k,
               new ItemBuilder(this.material(this.config.getString("clear.material"), Material.BARRIER))
                  .name(this.config.getString("clear.name", "&#FF0000&lCLEAR NAME GRADIENT"))
                  .lore(this.config.getStringList("clear.lore"))
                  .build()
            );
            namegradientsmodule$holder.actions.put(k, "clear");
         }
      }

      if (this.config.getBoolean("close.enabled", true)) {
         int l = this.config.getInt("close.slot", 31);
         if (l >= 0 && l < inventory.getSize()) {
            inventory.setItem(
               l,
               new ItemBuilder(this.material(this.config.getString("close.material"), Material.RED_STAINED_GLASS_PANE))
                  .name(this.config.getString("close.name", "&#FF0000&lCLOSE"))
                  .lore(this.config.getStringList("close.lore"))
                  .build()
            );
            namegradientsmodule$holder.actions.put(l, "close");
         }
      }

      this.play(player, "open");
      player.openInventory(inventory);
   }

   private ItemStack item(Player player, NameGradientsModule.GradientDef gradient) {
      boolean flag = this.owns(player, gradient);
      String s = BundleColorUtil.accentCode(gradient.dye);
      String s1 = flag ? this.config.getString("gui.status-owned", "&#94FF00&l&nOWNED") : this.config.getString("gui.status-locked", "&#FF0000&l&nLOCKED");
      String s2 = this.config.getString("gui.click-footer", "&eClick");
      List<String> list = new ArrayList<>();

      for (String s3 : this.config.getStringList("gui.lore")) {
         list.add(s3.replace("%color%", s).replace("%name%", gradient.name).replace("%status%", s1).replace("%click%", s2));
      }

      String s4 = this.config.getString("gui.item-name", "%color%&l%name%").replace("%color%", s).replace("%name%", gradient.name);
      Material material1 = this.material(this.config.getString("gui.item-material"), Material.BUNDLE);
      Material material = material1;
      if (this.config.getBoolean("gui.colored-bundles", true) && (material1 == Material.BUNDLE || material1.name().endsWith("_BUNDLE"))) {
         material = BundleColorUtil.bundleMaterial(gradient.dye);
      }

      ItemStack itemstack = new ItemBuilder(material).name(s4).lore(list).hideAll().build();
      if (this.config.getBoolean("gui.colored-bundles", true)) {
         try {
            BundleColorUtil.dye(itemstack, gradient.dye);
         } catch (Throwable throwable) {
         }
      }

      return itemstack;
   }

   private boolean owns(Player player, NameGradientsModule.GradientDef gradient) {
      return player.hasPermission(gradient.permission)
         || player.hasPermission("sharded.namegradient." + gradient.id)
         || player.hasPermission("sharded.namecolor." + gradient.id)
         || player.hasPermission("sharded.namegradient.admin")
         || player.isOp();
   }

   private boolean equip(Player player, String id) {
      NameGradientsModule.GradientDef namegradientsmodule$gradientdef = this.gradients.get(id);
      if (namegradientsmodule$gradientdef == null) {
         this.msg(player, "missing", "%name%", id);
         this.play(player, "error");
         return true;
      } else if (!this.owns(player, namegradientsmodule$gradientdef)) {
         this.msg(player, "locked", "%name%", namegradientsmodule$gradientdef.name);
         this.play(player, "error");
         return true;
      } else {
         if (this.plugin.cosmetics() != null) {
            this.plugin.cosmetics().setNameColor(player, namegradientsmodule$gradientdef.value);
         }

         this.msg(player, "set", "%name%", BundleColorUtil.accentCode(namegradientsmodule$gradientdef.dye) + "&l" + namegradientsmodule$gradientdef.name);
         this.play(player, "equip");
         return true;
      }
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         NameGradientsModule.Holder namegradientsmodule$holder = TrackedInventories.lookup(event.getView().getTopInventory(), NameGradientsModule.Holder.class);
         if (namegradientsmodule$holder != null) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
               String s = namegradientsmodule$holder.actions.get(event.getSlot());
               if (s != null) {
                  this.play(player, "click");
                  if (s.equals("clear")) {
                     if (this.plugin.cosmetics() != null) {
                        this.plugin.cosmetics().clearNameColor(player);
                     }

                     this.msg(player, "cleared");
                     player.closeInventory();
                  } else if (s.equals("close")) {
                     player.closeInventory();
                  } else if (s.startsWith("page:")) {
                     this.openOwned(player, Integer.parseInt(s.substring(5)));
                  } else {
                     if (s.startsWith("equip:")) {
                        player.closeInventory();
                        this.equip(player, s.substring(6));
                     }
                  }
               }
            }
         }
      }
   }

   private void msg(CommandSender sender, String key, String... replacements) {
      String s = this.config.getString("messages." + key);
      if (s != null && !s.isBlank()) {
         String s1 = this.config.getString("prefix", "");
         s = s.replace("%prefix%", s1);
         sender.sendMessage(Text.c(Text.apply(s, replacements)));
      } else {
         this.send(sender, key, replacements);
      }
   }

   private void play(Player player, String key) {
      if (this.config.getBoolean("sounds." + key + ".enabled", true)) {
         String s = this.config.getString("sounds." + key + ".sound", "ui.button.click");
         float f = (float)this.config.getDouble("sounds." + key + ".volume", 1.0);
         float f1 = (float)this.config.getDouble("sounds." + key + ".pitch", 1.0);

         try {
            player.playSound(player.getLocation(), Sound.valueOf(s.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT)), f, f1);
         } catch (IllegalArgumentException illegalargumentexception) {
            player.playSound(player.getLocation(), s, f, f1);
         }
      }
   }

   private Material material(String raw, Material fallback) {
      if (raw != null && !raw.isBlank()) {
         Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
         return material == null ? fallback : material;
      } else {
         return fallback;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1) {
         List<String> list = new ArrayList<>(List.of("set", "clear"));
         list.addAll(this.gradients.keySet());
         return TabCompleteHelper.filter(args[0], list);
      } else {
         return args.length == 2 && args[0].equalsIgnoreCase("set") ? TabCompleteHelper.filter(args[1], this.gradients.keySet()) : List.of();
      }
   }

   private static record GradientDef(String id, String name, String value, String hex, String hex2, String hex3, String permission, int slot, Color dye) {
   }

   private static final class Holder implements InventoryHolder {
      private final Map<Integer, String> actions = new LinkedHashMap<>();
      private Inventory inventory;
      private boolean owned;
      private int page;

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
