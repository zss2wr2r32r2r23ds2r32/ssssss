package com.sharded.core.modules.eglow;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

public final class EGlowModule extends Module implements CommandExecutor {
   private static final String MENU_TITLE = "Token Shop | Glows";
   private final Map<String, EGlowModule.GlowOption> glows = new LinkedHashMap<>();

   public EGlowModule(ShardedCore plugin) {
      super(plugin, "eglow");
   }

   @Override
   protected void onEnable() {
      this.migrateShopLayout();
      this.loadGlowOptions();
      this.registerCommand("eglow", this);
      this.registerCommand("glows", this);
      this.registerCommand("glowing", this);
   }

   @Override
   protected void onDisable() {
   }

   private void migrateShopLayout() {
      if (this.config.getInt("menu-rows", 4) < 6 || this.config.getBoolean("gui.fill", true) || this.config.getInt("disable.slot", 4) != 49) {
         this.config.set("config-version", 4);
         this.config.set("menu-rows", 6);
         this.config.set("gui.fill", false);
         this.config.set("gui.colored-harnesses", true);
         this.config.set("disable.slot", 49);
         this.applyGlow("red", 10, "RED_HARNESS");
         this.applyGlow("darkred", 11, "RED_HARNESS");
         this.applyGlow("yellow", 12, "YELLOW_HARNESS");
         this.applyGlow("gold", 13, "ORANGE_HARNESS");
         this.applyGlow("green", 14, "LIME_HARNESS");
         this.applyGlow("darkgreen", 15, "LIME_HARNESS");
         this.applyGlow("cyan", 16, "CYAN_HARNESS");
         this.applyGlow("blue", 19, "BLUE_HARNESS");
         this.applyGlow("darkblue", 20, "BLUE_HARNESS");
         this.applyGlow("purple", 21, "PURPLE_HARNESS");
         this.applyGlow("pink", 22, "PINK_HARNESS");
         this.applyGlow("white", 23, "WHITE_HARNESS");
         this.applyGlow("gray", 24, "GRAY_HARNESS");
         this.applyGlow("black", 25, "BLACK_HARNESS");
         this.applyGlow("rainbow", 28, "PINK_HARNESS");

         try {
            this.config.save(new File(this.moduleFolder(), "config.yml"));
         } catch (Exception exception) {
         }
      }
   }

   private void applyGlow(String id, int slot, String material) {
      if (this.config.getConfigurationSection("glows." + id) != null) {
         this.config.set("glows." + id + ".slot", slot);
         this.config.set("glows." + id + ".material", material);
      }
   }

   private void loadGlowOptions() {
      this.glows.clear();
      ConfigurationSection configurationsection = this.config.getConfigurationSection("glows");
      if (configurationsection != null) {
         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            if (configurationsection1 != null) {
               this.glows
                  .put(
                     s,
                     new EGlowModule.GlowOption(
                        s,
                        configurationsection1.getInt("slot", 0),
                        configurationsection1.getString("permission", "eglow.color." + s),
                        configurationsection1.getString("command", "eglow:eglow " + s),
                        configurationsection1.getString("display-name", "&f" + s),
                        configurationsection1.getStringList("lore"),
                        this.parseColor(configurationsection1.getString("color", "#FFFFFF")),
                        configurationsection1.getString("material", "")
                     )
                  );
            }
         }
      }
   }

   private Color parseColor(String raw) {
      String s = ColorUtil.normalize(raw).replace("§x", "").replace("&x", "").replace("&", "").replace("§", "");
      s = s.replaceAll("[^0-9A-Fa-f]", "");
      if (s.length() >= 6) {
         try {
            return Color.fromRGB(Integer.parseInt(s.substring(0, 2), 16), Integer.parseInt(s.substring(2, 4), 16), Integer.parseInt(s.substring(4, 6), 16));
         } catch (NumberFormatException numberformatexception) {
         }
      }

      return Color.WHITE;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("eglow.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else if (args.length == 0) {
            this.openMenu(player);
            return true;
         } else {
            String s = args[0].toLowerCase(Locale.ROOT);
            if (!s.equals("off") && !s.equals("disabled") && !s.equals("disable")) {
               EGlowModule.GlowOption eglowmodule$glowoption = this.glows.get(s);
               if (eglowmodule$glowoption == null) {
                  this.openMenu(player);
                  return true;
               } else {
                  this.applyGlow(player, eglowmodule$glowoption);
                  return true;
               }
            } else {
               this.runGlowCommand(player, this.config.getString("disable-command", "eglow:eglow disabled"));
               this.send(player, "disabled", new String[0]);
               return true;
            }
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   public void openMenu(Player player) {
      int i = Math.max(1, Math.min(6, this.config.getInt("menu-rows", 6)));
      EGlowModule.GlowMenuHolder eglowmodule$glowmenuholder = new EGlowModule.GlowMenuHolder();
      Inventory inventory = this.plugin.getServer().createInventory(eglowmodule$glowmenuholder, i * 9, Text.c("Token Shop | Glows"));
      TrackedInventories.track(inventory, eglowmodule$glowmenuholder);
      if (this.config.getBoolean("gui.fill", false)) {
         Material material = Material.matchMaterial(this.config.getString("filler-material", "BLACK_STAINED_GLASS_PANE"));
         if (material == null) {
            material = Material.BLACK_STAINED_GLASS_PANE;
         }

         ItemStack itemstack = new ItemBuilder(material).name(" ").hideAll().build();

         for (int j = 0; j < inventory.getSize(); j++) {
            inventory.setItem(j, itemstack.clone());
         }
      }

      Map<String, String> map = this.equipPlaceholders(player);

      for (EGlowModule.GlowOption eglowmodule$glowoption : this.glows.values()) {
         List<String> list = this.applyPlaceholders(eglowmodule$glowoption.lore(), map);
         inventory.setItem(eglowmodule$glowoption.slot(), this.glowIcon(eglowmodule$glowoption, list));
      }

      List<String> list1 = this.applyPlaceholders(this.config.getStringList("disable.lore"), map);
      inventory.setItem(
         this.config.getInt("disable.slot", 49),
         new ItemBuilder(Material.BARRIER).name(this.config.getString("disable.display-name", "&#FF0000&lDISABLE GLOW")).lore(list1).hideAll().build()
      );
      player.openInventory(inventory);
   }

   private ItemStack glowIcon(EGlowModule.GlowOption glow, List<String> lore) {
      Material material = Material.matchMaterial(glow.material());
      return material != null && material != Material.AIR && material != Material.PAPER
         ? new ItemBuilder(material).name(glow.displayName()).lore(lore).hideAll().build()
         : this.leatherChestplate(glow.color(), glow.displayName(), lore);
   }

   private List<String> applyPlaceholders(List<String> lines, Map<String, String> placeholders) {
      ArrayList<String> arraylist = new ArrayList<>(lines.size());

      for (String s : lines) {
         String s1 = s;

         for (Entry<String, String> entry : placeholders.entrySet()) {
            s1 = s1.replace("%" + entry.getKey() + "%", entry.getValue());
         }

         arraylist.add(s1);
      }

      return arraylist;
   }

   public Map<String, String> equipPlaceholders(Player player) {
      LinkedHashMap<String, String> linkedhashmap = new LinkedHashMap<>();
      String s = this.config.getString("placeholders.owned-yes", "&#9FFF00Yes");
      String s1 = this.config.getString("placeholders.owned-no", "&#FF2727No");

      for (EGlowModule.GlowOption eglowmodule$glowoption : this.glows.values()) {
         linkedhashmap.put("glow_owned_" + eglowmodule$glowoption.id(), player.hasPermission(eglowmodule$glowoption.permission()) ? s : s1);
      }

      return linkedhashmap;
   }

   private ItemStack leatherChestplate(Color color, String name, List<String> lore) {
      ItemStack itemstack = new ItemStack(Material.LEATHER_CHESTPLATE);
      if (itemstack.getItemMeta() instanceof LeatherArmorMeta leatherarmormeta) {
         leatherarmormeta.setColor(color);
         itemstack.setItemMeta(leatherarmormeta);
      }

      return new ItemBuilder(itemstack).name(name).lore(lore).hideAll().build();
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         if (TrackedInventories.lookup(event.getView().getTopInventory(), EGlowModule.GlowMenuHolder.class) != null) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
               int i = event.getSlot();
               if (i == this.config.getInt("disable.slot", 49)) {
                  player.closeInventory();
                  this.runGlowCommand(player, this.config.getString("disable-command", "eglow:eglow disabled"));
                  this.send(player, "disabled", new String[0]);
               } else {
                  for (EGlowModule.GlowOption eglowmodule$glowoption : this.glows.values()) {
                     if (eglowmodule$glowoption.slot() == i) {
                        player.closeInventory();
                        this.applyGlow(player, eglowmodule$glowoption);
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (TrackedInventories.lookup(event.getView().getTopInventory(), EGlowModule.GlowMenuHolder.class) != null) {
         event.setCancelled(true);
      }
   }

   private void applyGlow(Player player, EGlowModule.GlowOption glow) {
      if (!player.hasPermission(glow.permission())) {
         this.send(player, "no-color-permission", new String[]{"%color%", glow.id()});
      } else {
         this.runGlowCommand(player, glow.command());
         this.send(player, "applied", new String[]{"%color%", glow.displayName()});
      }
   }

   private void runGlowCommand(Player player, String command) {
      if (command.startsWith("/")) {
         command = command.substring(1);
      }

      player.performCommand(command);
   }

   private static final class GlowMenuHolder implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   private static record GlowOption(String id, int slot, String permission, String command, String displayName, List<String> lore, Color color, String material) {
   }
}
