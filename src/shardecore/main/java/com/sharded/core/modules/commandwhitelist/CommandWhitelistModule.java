package com.sharded.core.modules.commandwhitelist;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class CommandWhitelistModule extends Module {
   private Set<String> allowed = Set.of();
   private Set<String> alwaysAllowed = Set.of();

   public CommandWhitelistModule(ShardedCore plugin) {
      super(plugin, "commandwhitelist");
   }

   @Override
   protected void onEnable() {
      this.ensureListed(
         "stats",
         "rules",
         "leaderboard",
         "cosmetics",
         "cosmetic",
         "namecolor",
         "namecolors",
         "wardrobe",
         "hatshop",
         "hatcosmetics",
         "pets",
         "pet",
         "temprank",
         "rankshop",
         "temprankshop",
         "glows",
         "glowing",
         "graves",
         "itemedit",
         "ie",
         "serveritem",
         "si",
         "itemstorage",
         "is",
         "crystalshop",
         "lb",
         "lbsetline",
         "setline",
         "lbremove",
         "lbplace",
         "lbpurge",
         "lbclean",
         "k",
         "itemshop",
         "ishop",
         "cosmeticshop",
         "dailyitemshop",
         "rotatingshop",
         "crates",
         "crate",
         "scrates"
      );
      this.removeListed(
         "leaderboards",
         "lbtopper", "lblock", "lbunlock", "mv", "multiverse", "hide", "whois",
         "tpa", "tpahere", "tpaccept", "tpyes", "tpacancel", "tpdeny",
         "tpatoggle", "tpauto", "tpaheretoggle",
         "mobspawning", "mobtoggle", "mtoggle", "mobspawn",
         "crystaltoggle", "fastcrystal", "paytoggle"
      );
      this.reloadLists();
      this.registerListener(this);
   }

   private void ensureListed(String... commands) {
      List<String> list = new ArrayList<>(this.config.getStringList("commands"));
      boolean flag = false;

      for (String s : commands) {
         boolean flag1 = false;

         for (String s1 : list) {
            if (s1 != null && s1.equalsIgnoreCase(s)) {
               flag1 = true;
               break;
            }
         }

         if (!flag1) {
            list.add(s);
            flag = true;
         }
      }

      if (flag) {
         this.config.set("commands", list);

         try {
            this.config.save(new File(this.moduleFolder(), "config.yml"));
         } catch (Exception exception) {
         }
      }
   }

   private void removeListed(String... commands) {
      List<String> list = new ArrayList<>(this.config.getStringList("commands"));
      boolean flag = list.removeIf(entry -> {
         if (entry == null) {
            return false;
         } else {
            for (String s : commands) {
               if (entry.equalsIgnoreCase(s)) {
                  return true;
               }
            }

            return false;
         }
      });
      if (flag) {
         this.config.set("commands", list);

         try {
            this.config.save(new File(this.moduleFolder(), "config.yml"));
         } catch (Exception exception) {
         }
      }
   }

   @Override
   protected void onDisable() {
      this.allowed = Set.of();
      this.alwaysAllowed = Set.of();
   }

   public void reloadLists() {
      Set<String> set = new HashSet<>();

      for (String s : this.config.getStringList("commands")) {
         if (s != null && !s.isBlank()) {
            set.add(s.toLowerCase(Locale.ROOT));
         }
      }

      Set<String> set1 = new HashSet<>();

      for (String s1 : this.config.getStringList("always-allowed")) {
         if (s1 != null && !s1.isBlank()) {
            set1.add(s1.toLowerCase(Locale.ROOT));
         }
      }

      this.allowed = Set.copyOf(set);
      this.alwaysAllowed = Set.copyOf(set1);
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      if (this.config.getBoolean("enabled", true)) {
         Player player = event.getPlayer();
         if (!player.isOp() || !this.config.getBoolean("ops-bypass", true)) {
            String s = this.config.getString("bypass-permission", "sharded.commandwhitelist.bypass");
            if (!player.hasPermission(s) && !player.hasPermission("sharded.commandwhitelist.bypass")) {
               String s1 = this.label(event.getMessage());
               if (!s1.isEmpty()) {
                  if (!this.alwaysAllowed.contains(s1) && !this.allowed.contains(s1)) {
                     event.setCancelled(true);
                     this.send(player, "blocked", new String[]{"%command%", s1});
                  }
               }
            }
         }
      }
   }

   private String label(String message) {
      String s = message.startsWith("/") ? message.substring(1) : message;
      int i = s.indexOf(32);
      String s1 = i >= 0 ? s.substring(0, i) : s;
      int j = s1.indexOf(58);
      return (j >= 0 ? s1.substring(j + 1) : s1).toLowerCase(Locale.ROOT);
   }
}
