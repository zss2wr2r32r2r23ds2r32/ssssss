package com.sharded.core.modules.serverlinks;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.HeadUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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

public final class ServerLinksModule extends Module implements CommandExecutor {
   public ServerLinksModule(ShardedCore plugin) {
      super(plugin, "serverlinks");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("apply", this);
      this.registerCommand("discord", this);
      this.registerCommand("store", this);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);
      if (s.equals("apply")) {
         this.broadcastSection("apply", sender instanceof Player player ? player.getName() : "Console");
         return true;
      } else if (s.equals("discord")) {
         this.sendLink(sender, "discord");
         return true;
      } else if (s.equals("store")) {
         this.sendLink(sender, "store");
         return true;
      } else {
         return false;
      }
   }

   private void sendLink(CommandSender sender, String key) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("links." + key);
      if (configurationsection == null) {
         this.send(sender, "missing", new String[]{"%link%", key});
      } else {
         String s = configurationsection.getString("url", "");
         List<String> list = configurationsection.getStringList("messages");
         if (list.isEmpty()) {
            list = List.of(configurationsection.getString("message", "&b" + key + ": %url%"));
         }

         for (String s1 : list) {
            Component component = this.clickable(s1, s);
            if (sender instanceof Player player) {
               player.sendMessage(component);
            } else {
               sender.sendMessage(Text.c(Text.apply(s1, "%url%", s)));
            }
         }
      }
   }

   private void broadcastSection(String key, String playerName) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("broadcasts." + key);
      if (configurationsection != null) {
         String s = configurationsection.getString("url", "");
         List<String> list = configurationsection.getStringList("lines");
         if (list.isEmpty()) {
            list = configurationsection.getStringList("messages");
         }

         for (String s1 : list) {
            String s2 = Text.apply(s1, "%player%", playerName, "%url%", s);
            Component component = s.isBlank() ? Text.c(s2) : this.clickable(s2, s);
            Bukkit.broadcast(component);
         }
      }
   }

   private Component clickable(String raw, String url) {
      Component component = Text.c(raw.replace("%url%", url));
      if (raw.contains("%url%") && !url.isBlank()) {
         component = component.clickEvent(ClickEvent.openUrl(url));
         String s = this.config.getString("link-hover", "&fClick to open");
         component = component.hoverEvent(HoverEvent.showText(Text.c(s)));
      }

      return component;
   }

   private void openGui(Player player, String menu) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("gui." + menu);
      if (configurationsection == null) {
         this.send(player, "missing", new String[]{"%link%", menu});
      } else {
         int i = Math.max(1, Math.min(6, configurationsection.getInt("rows", 3)));
         ServerLinksModule.Holder serverlinksmodule$holder = new ServerLinksModule.Holder();
         Inventory inventory = Bukkit.createInventory(serverlinksmodule$holder, i * 9, Text.c(configurationsection.getString("title", "&8Links")));
         serverlinksmodule$holder.inventory = inventory;
         TrackedInventories.track(inventory, serverlinksmodule$holder);
         ItemStack itemstack = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();

         for (int j = 0; j < inventory.getSize(); j++) {
            inventory.setItem(j, itemstack);
         }

         ConfigurationSection configurationsection2 = configurationsection.getConfigurationSection("items");
         if (configurationsection2 != null) {
            for (String s : configurationsection2.getKeys(false)) {
               ConfigurationSection configurationsection1 = configurationsection2.getConfigurationSection(s);
               if (configurationsection1 != null) {
                  int k = configurationsection1.getInt("slot", -1);
                  if (k >= 0 && k < inventory.getSize()) {
                     String s1 = configurationsection1.getString("material", "PAPER");
                     ItemStack itemstack1 = HeadUtil.parse(s1);
                     if (itemstack1 == null) {
                        Material material = Material.matchMaterial(s1.toUpperCase(Locale.ROOT));
                        if (material == null) {
                           material = Material.PAPER;
                        }

                        itemstack1 = new ItemBuilder(material)
                           .name(configurationsection1.getString("name", s))
                           .lore(configurationsection1.getStringList("lore"))
                           .build();
                     } else {
                        itemstack1 = new ItemBuilder(itemstack1)
                           .name(configurationsection1.getString("name", s))
                           .lore(configurationsection1.getStringList("lore"))
                           .build();
                     }

                     inventory.setItem(k, itemstack1);
                     serverlinksmodule$holder.actions.put(k, configurationsection1.getString("url", configurationsection1.getString("command", "")));
                  }
               }
            }
         }

         player.openInventory(inventory);
      }
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         ServerLinksModule.Holder serverlinksmodule$holder = TrackedInventories.lookup(event.getView().getTopInventory(), ServerLinksModule.Holder.class);
         if (serverlinksmodule$holder != null) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
               String s = serverlinksmodule$holder.actions.get(event.getSlot());
               if (s != null && !s.isBlank()) {
                  if (s.startsWith("http")) {
                     player.sendMessage(this.clickable("&aOpening link...", s));
                  } else {
                     player.performCommand(s.startsWith("/") ? s.substring(1) : s);
                  }

                  player.closeInventory();
               }
            }
         }
      }
   }

   private static final class Holder implements InventoryHolder {
      private final Map<Integer, String> actions = new HashMap<>();
      private Inventory inventory;

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
