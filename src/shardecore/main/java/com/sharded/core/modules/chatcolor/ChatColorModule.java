package com.sharded.core.modules.chatcolor;

import com.sharded.core.ShardedCore;
import com.sharded.core.cosmetics.CosmeticDatabase;
import com.sharded.core.cosmetics.CosmeticService;
import com.sharded.core.module.Module;
import com.sharded.core.util.BundleColorUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class ChatColorModule extends Module implements CommandExecutor, TabCompleter {
   private static final int[] CONTENT = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
   private static final int[] OWNED_CONTENT = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
   private final Map<String, ChatColorModule.ColorDef> colors = new LinkedHashMap<>();

   public ChatColorModule(ShardedCore plugin) {
      super(plugin, "chatcolor");
   }

   @Override
   protected void onEnable() {
      this.reloadColors();
      this.registerCommand("chatcolor", this);
      this.registerCommand("chatcolors", this);
      this.registerListener(this);
   }

   private void reloadColors() {
      this.loadConfigs();
      this.colors.clear();
      ConfigurationSection configurationsection = this.config.getConfigurationSection("colors");
      if (configurationsection != null) {
         int i = 10;

         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            if (configurationsection1 != null) {
               String s1 = configurationsection1.getString("type", "solid");
               String s2 = configurationsection1.getString("hex", this.config.getString("gui.default-color", "0083FF"));
               String s3 = configurationsection1.getString("hex2", s2);
               Color color = BundleColorUtil.fromHex(s2);
               String s4;
               if ("gradient".equalsIgnoreCase(s1)) {
                  s4 = "#" + s2.replace("#", "") + " #" + s3.replace("#", "");
               } else {
                  s4 = configurationsection1.getString("value", "&#" + s2.replace("#", ""));
                  if (!s4.startsWith("&#") && !s4.startsWith("#")) {
                     s4 = "&#" + s2.replace("#", "");
                  }
               }

               int j = configurationsection1.contains("slot") ? configurationsection1.getInt("slot") : i++;
               if (i == 17) {
                  i = 19;
               }

               this.colors
                  .put(
                     s.toLowerCase(Locale.ROOT),
                     new ChatColorModule.ColorDef(
                        s.toLowerCase(Locale.ROOT),
                        s.replace('_', ' ').toUpperCase(Locale.ROOT),
                        s1,
                        s4,
                        s2,
                        s3,
                        configurationsection1.getString("permission", "sharded.chatcolor." + s.toLowerCase(Locale.ROOT)),
                        j,
                        color
                     )
                  );
            }
         }
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length == 0) {
         if (sender instanceof Player player1) {
            if (!player1.hasPermission("sharded.chatcolor.use") && !player1.isOp()) {
               this.msg(player1, "no-permission");
               return true;
            } else {
               try {
                  this.openMenu(player1, 0);
               } catch (Throwable throwable) {
                  this.plugin.getLogger().severe("[chatcolor] Failed to open GUI for " + player1.getName() + ": " + throwable.getMessage());
                  throwable.printStackTrace();
                  player1.sendMessage(Text.c("&#FF0000&lERROR &8▷ &fCould not open chat colors. Check console."));
               }

               return true;
            }
         } else {
            this.msg(sender, "players-only");
            return true;
         }
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);
         if (s.equals("create") && args.length >= 3) {
            if (!sender.hasPermission("sharded.chatcolor.admin")) {
               this.msg(sender, "no-permission");
               return true;
            } else {
               return this.createSolid(sender, args[1], args[2]);
            }
         } else if (s.equals("gradient") && args.length >= 5 && args[1].equalsIgnoreCase("create")) {
            if (!sender.hasPermission("sharded.chatcolor.admin")) {
               this.msg(sender, "no-permission");
               return true;
            } else {
               return this.createGradient(sender, args[2], args[3], args[4]);
            }
         } else if (s.equals("set") && args.length >= 2) {
            if (sender instanceof Player player3) {
               return this.equip(player3, args[1].toLowerCase(Locale.ROOT));
            } else {
               this.msg(sender, "players-only");
               return true;
            }
         } else if ((s.equals("remove") || s.equals("delete")) && args.length >= 2) {
            if (!sender.hasPermission("sharded.chatcolor.admin")) {
               this.msg(sender, "no-permission");
               return true;
            } else {
               String s1 = args[1].toLowerCase(Locale.ROOT);
               if (this.config.getConfigurationSection("colors." + s1) == null) {
                  this.msg(sender, "missing", "%color%", s1);
                  return true;
               } else {
                  this.config.set("colors." + s1, null);
                  this.saveConfigFile();
                  this.reloadColors();
                  this.msg(sender, "removed", "%color%", s1);
                  return true;
               }
            }
         } else if (!s.equals("clear") && !s.equals("reset")) {
            if (sender instanceof Player player2 && this.colors.containsKey(s)) {
               return this.equip(player2, s);
            }

            this.msg(sender, "usage");
            return true;
         } else if (sender instanceof Player player) {
            if (this.plugin.cosmetics() != null) {
               this.plugin.cosmetics().clearChatColor(player);
            }

            this.msg(player, "cleared");
            return true;
         } else {
            this.msg(sender, "players-only");
            return true;
         }
      }
   }

   private boolean createSolid(CommandSender sender, String rawId, String hexRaw) {
      String s = rawId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
      Color color = BundleColorUtil.parse(hexRaw);
      if (!s.isBlank() && color != null) {
         String s1 = String.format("%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
         this.config.set("colors." + s + ".type", "solid");
         this.config.set("colors." + s + ".hex", s1);
         this.config.set("colors." + s + ".value", "&#" + s1);
         this.saveConfigFile();
         this.reloadColors();
         this.msg(sender, "created", "%color%", s, "%hex%", "#" + s1);
         return true;
      } else {
         this.msg(sender, "invalid");
         return true;
      }
   }

   private boolean createGradient(CommandSender sender, String rawId, String fromRaw, String toRaw) {
      String s = rawId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
      Color color = BundleColorUtil.parse(fromRaw);
      Color color1 = BundleColorUtil.parse(toRaw);
      if (!s.isBlank() && color != null && color1 != null) {
         String s1 = String.format("%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
         String s2 = String.format("%02X%02X%02X", color1.getRed(), color1.getGreen(), color1.getBlue());
         this.config.set("colors." + s + ".type", "gradient");
         this.config.set("colors." + s + ".hex", s1);
         this.config.set("colors." + s + ".hex2", s2);
         this.saveConfigFile();
         this.reloadColors();
         this.msg(sender, "created-gradient", "%color%", s, "%from%", "#" + s1, "%to%", "#" + s2);
         return true;
      } else {
         this.msg(sender, "invalid");
         return true;
      }
   }

   private void saveConfigFile() {
      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (Exception exception) {
      }
   }

   public void openOwned(Player player) {
      this.openMenu(player, 0, true);
   }

   private void openMenu(Player player, int page) {
      this.openMenu(player, page, false);
   }

   private void openMenu(Player player, int page, boolean ownedOnly) {
      List<ChatColorModule.ColorDef> list = new ArrayList<>();
      for (ChatColorModule.ColorDef color : this.colors.values()) {
         if (!ownedOnly || this.owns(player, color)) {
            list.add(color);
         }
      }
      int i = ownedOnly ? 6 : Math.max(1, Math.min(6, this.config.getInt("gui.rows", 4)));
      ChatColorModule.Holder chatcolormodule$holder = new ChatColorModule.Holder(page, ownedOnly);
      String title = ownedOnly
         ? this.config.getString("gui.owned-title", "&#FF0000&lYour Chat Colors")
         : this.config.getString("gui.title", "Chat Colors");
      Inventory inventory = Bukkit.createInventory(chatcolormodule$holder, i * 9, Text.c(title));
      chatcolormodule$holder.inventory = inventory;
      TrackedInventories.track(inventory, chatcolormodule$holder);
      ItemStack itemstack = new ItemBuilder(this.material(this.config.getString("filler.material"), Material.BLACK_STAINED_GLASS_PANE))
         .name(this.config.getString("filler.name", " "))
         .build();

      for (int j = 0; j < inventory.getSize(); j++) {
         inventory.setItem(j, itemstack);
      }

      int[] slots = ownedOnly ? OWNED_CONTENT : CONTENT;
      int j1 = slots.length;
      int k = Math.max(0, (list.size() + j1 - 1) / j1 - 1);
      page = Math.max(0, Math.min(page, k));
      chatcolormodule$holder.page = page;
      int l = page * j1;

      for (int i1 = 0; i1 < j1 && l + i1 < list.size(); i1++) {
         ChatColorModule.ColorDef chatcolormodule$colordef = list.get(l + i1);
         inventory.setItem(slots[i1], this.colorItem(player, chatcolormodule$colordef));
         chatcolormodule$holder.actions.put(slots[i1], "equip:" + chatcolormodule$colordef.id);
      }

      if (ownedOnly && list.isEmpty()) {
         inventory.setItem(
            22,
            new ItemBuilder(Material.RED_DYE)
               .name("&#FF0000&lNO CHAT COLORS")
               .lore("&8Description", "", "&#FF0000| &fYou do not own any chat colors yet.")
               .build()
         );
      }

      if (this.config.getBoolean("clear.enabled", true)) {
         int k1 = ownedOnly ? 49 : this.config.getInt("clear.slot", 4);
         inventory.setItem(
            k1,
            new ItemBuilder(this.material(this.config.getString("clear.material"), Material.BARRIER))
               .name(this.config.getString("clear.name", "&#FF0000&lCLEAR CHATCOLOR"))
               .lore(this.config.getStringList("clear.lore"))
               .build()
         );
         chatcolormodule$holder.actions.put(k1, "clear");
      }

      if (this.config.getBoolean("close.enabled", true) && !ownedOnly) {
         int l1 = this.config.getInt("close.slot", 31);
         inventory.setItem(
            l1,
            new ItemBuilder(this.material(this.config.getString("close.material"), Material.RED_STAINED_GLASS_PANE))
               .name(this.config.getString("close.name", "&#FF0000&lCLOSE"))
               .lore(this.config.getStringList("close.lore"))
               .build()
         );
         chatcolormodule$holder.actions.put(l1, "close");
      }

      if (k > 0) {
         int i2 = ownedOnly ? 45 : this.config.getInt("previous.slot", 30);
         int j2 = ownedOnly ? 53 : this.config.getInt("next.slot", 32);
         if (page > 0) {
            inventory.setItem(i2, new ItemBuilder(Material.ARROW).name("&cPrevious").build());
            chatcolormodule$holder.actions.put(i2, "page:" + (page - 1));
         }

         if (page < k) {
            inventory.setItem(j2, new ItemBuilder(Material.ARROW).name("&aNext").build());
            chatcolormodule$holder.actions.put(j2, "page:" + (page + 1));
         }
      }

      this.play(player, "open");
      player.openInventory(inventory);
   }

   private ItemStack colorItem(Player player, ChatColorModule.ColorDef color) {
      boolean flag = this.owns(player, color);
      String s = BundleColorUtil.accentCode(color.dye);
      String s1 = flag ? this.config.getString("gui.status-owned", "&#94FF00&l&nOWNED") : this.config.getString("gui.status-locked", "&#FF0000&l&nLOCKED");
      String s2 = this.config.getString("gui.click-footer", "&eClick");
      List<String> list = new ArrayList<>();

      for (String s3 : this.config.getStringList("gui.lore")) {
         list.add(s3.replace("%color%", s).replace("%name%", color.name).replace("%status%", s1).replace("%click%", s2));
      }

      String s4 = this.config.getString("gui.item-name", "%color%&l%name%").replace("%color%", s).replace("%name%", color.name);
      Material material1 = this.material(this.config.getString("gui.item-material"), Material.BUNDLE);
      Material material = material1;
      if (this.config.getBoolean("gui.colored-bundles", true) && (material1 == Material.BUNDLE || material1.name().endsWith("_BUNDLE"))) {
         material = BundleColorUtil.bundleMaterial(color.dye);
      }

      ItemStack itemstack = new ItemBuilder(material).name(s4).lore(list).hideAll().build();
      if (this.config.getBoolean("gui.colored-bundles", true)) {
         BundleColorUtil.dye(itemstack, color.dye);
      }

      return itemstack;
   }

   private boolean owns(Player player, ChatColorModule.ColorDef color) {
      return "default".equalsIgnoreCase(color.id)
         ? true
         : player.hasPermission(color.permission)
            || player.hasPermission("sharded.chatcolor." + color.id)
            || player.hasPermission("ezcolor.color." + color.id)
            || player.hasPermission("sharded.chatcolor.admin")
            || player.isOp();
   }

   private boolean equip(Player player, String id) {
      ChatColorModule.ColorDef chatcolormodule$colordef = this.colors.get(id);
      if (chatcolormodule$colordef == null) {
         this.msg(player, "missing", "%color%", id);
         this.play(player, "error");
         return true;
      } else if (!this.owns(player, chatcolormodule$colordef)) {
         this.msg(player, "locked", "%color%", chatcolormodule$colordef.name);
         this.play(player, "error");
         return true;
      } else {
         if (this.plugin.cosmetics() != null) {
            this.plugin.cosmetics().setChatColor(player, chatcolormodule$colordef.value);
         }

         this.msg(player, "set", "%color%", BundleColorUtil.accentCode(chatcolormodule$colordef.dye) + "&l" + chatcolormodule$colordef.name);
         this.play(player, "equip");
         return true;
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onChatColor(AsyncChatEvent event) {
      if (this.plugin.cosmetics() != null) {
         String s = PlainTextComponentSerializer.plainText().serialize(event.message());
         if (!s.isEmpty() && !CosmeticService.looksLikeCommand(s)) {
            String s1 = this.plugin.cosmetics().chatColorPrefix(event.getPlayer().getUniqueId());
            String s2 = null;

            try {
               CosmeticDatabase cosmeticdatabase = this.plugin.cosmetics().database();
               if (cosmeticdatabase != null) {
                  s2 = cosmeticdatabase.get(event.getPlayer().getUniqueId()).chatColor();
               }
            } catch (Throwable throwable) {
            }

            if (s2 == null || s2.isBlank()) {
               if (s1 == null || s1.isBlank()) {
                  return;
               }

               s2 = s1;
            }

            event.message(Text.c(CosmeticService.colorizeChat(s, s2)));
         }
      }
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         ChatColorModule.Holder chatcolormodule$holder = TrackedInventories.lookup(event.getView().getTopInventory(), ChatColorModule.Holder.class);
         if (chatcolormodule$holder != null) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
               String s = chatcolormodule$holder.actions.get(event.getSlot());
               if (s != null) {
                  this.play(player, "click");
                  if (s.equals("clear")) {
                     if (this.plugin.cosmetics() != null) {
                        this.plugin.cosmetics().clearChatColor(player);
                     }

                     this.msg(player, "cleared");
                     player.closeInventory();
                  } else if (s.equals("close")) {
                     player.closeInventory();
                  } else if (s.startsWith("page:")) {
                     int i = Integer.parseInt(s.substring(5));
                     player.closeInventory();
                     this.openMenu(player, i, chatcolormodule$holder.owned);
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
         return TabCompleteHelper.filter(args[0], "create", "set", "remove", "clear", "gradient");
      } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
         return TabCompleteHelper.filter(args[1], this.colors.keySet());
      } else {
         return args.length == 2 && args[0].equalsIgnoreCase("gradient") ? TabCompleteHelper.filter(args[1], "create") : List.of();
      }
   }

   private static record ColorDef(String id, String name, String type, String value, String hex, String hex2, String permission, int slot, Color dye) {
   }

   private static final class Holder implements InventoryHolder {
      private int page;
      private final boolean owned;
      private final Map<Integer, String> actions = new LinkedHashMap<>();
      private Inventory inventory;

      private Holder(int page, boolean owned) {
         this.page = page;
         this.owned = owned;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
