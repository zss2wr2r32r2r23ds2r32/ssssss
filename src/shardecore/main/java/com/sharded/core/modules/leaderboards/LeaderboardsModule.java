package com.sharded.core.modules.leaderboards;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.scheduler.BukkitTask;

public final class LeaderboardsModule extends Module implements CommandExecutor, TabCompleter {
   private LeaderboardService service;
   private LeaderboardGuiHandler gui;
   private BukkitTask refreshTask;

   public LeaderboardsModule(ShardedCore plugin) {
      super(plugin, "leaderboards");
   }

   public LeaderboardService service() {
      return this.service;
   }

   ShardedCore plugin() {
      return this.plugin;
   }

   public int teamRank(int teamId) {
      return this.service != null ? this.service.teamRank(teamId) : -1;
   }

   public String hologramLineTemplate(String type) {
      return this.config.getString("hologram." + type + "-line", "&a#%rank% &f%name% &7— &f%value% %label%");
   }

   public String placeholder(String type, int rank, String field) {
      if (this.service == null || type == null || field == null || rank < 1) {
         return "";
      }
      List<LeaderboardService.Entry> list = this.service.entries(type);
      if (rank > list.size()) {
         return "value".equals(field) ? "0" : "";
      }
      LeaderboardService.Entry entry = list.get(rank - 1);
      return switch (field.toLowerCase(Locale.ROOT)) {
         case "value" -> this.service.formatValue(type, entry.value());
         case "uuid", "head_uuid" -> entry.uuid() == null ? "" : entry.uuid().toString();
         default -> entry.displayName() == null ? "" : entry.displayName();
      };
   }

   @Override
   protected void onEnable() {
      this.migrateLayout();
      this.service = new LeaderboardService(this.plugin, this.config);
      this.gui = new LeaderboardGuiHandler(this, this.service, this.config);
      this.registerCommand("leaderboard", this);
   }

   private void migrateLayout() {
      if (this.config.getInt("config-version", 0) >= 18) {
         return;
      }
      if (this.config.getInt("config-version", 0) >= 16) {
         this.config.set("refresh-slot", -1);
         this.config.set("hub-refresh-slot", -1);
         this.config.set("config-version", 18);
         try {
            this.config.save(new java.io.File(this.moduleFolder(), "config.yml"));
         } catch (Exception ignored) {
         }
         return;
      }
      if (this.config.getInt("config-version", 0) < 8) {
         this.config.set("rows", 5);
         this.config.set("board-rows", 6);
         this.config.set("back-slot", 45);
         this.config.set("you-slot", 49);
         this.config.set("lore-version", 5);
         this.config.set("gui-title", "&8Leaderboards");
         this.config.set("you-name", "%color%&lYOUR %score_label%");
         this.config.set("you-lore", List.of(
               "&8Description",
               "",
               "%color%Information:",
               "%color%| &fYour %score_label% on this board",
               "",
               "%color%⚓ &fRank: %color%%rank%",
               "%color%☀ &f%score_label%: %color%%score%",
               "",
               "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To View Stats"
         ));
         this.config.set("boards.kills.slot", 20);
         this.config.set("boards.kills.material", "NETHERITE_SWORD");
         this.config.set("boards.kills.title", "Kills");
         this.config.set("boards.playtime.slot", 21);
         this.config.set("boards.playtime.material", "CLOCK");
         this.config.set("boards.playtime.title", "Playtime");
         this.config.set("boards.totems.slot", 22);
         this.config.set("boards.totems.material", "TOTEM_OF_UNDYING");
         this.config.set("boards.totems.title", "Totem Pops");
         this.config.set("boards.killstreaks.slot", 23);
         this.config.set("boards.killstreaks.material", "MACE");
         this.config.set("boards.killstreaks.title", "Killstreaks");
         this.config.set("boards.teams.slot", 24);
         this.config.set("boards.teams.material", "PURPLE_BANNER");
         this.config.set("boards.teams.title", "Team");
         this.config.set("boards.deaths.slot", 30);
         this.config.set("boards.deaths.material", "SKELETON_SKULL");
         this.config.set("boards.deaths.title", "Deaths");
         this.config.set("boards.tokens.slot", 32);
         this.config.set("boards.tokens.material", "LIGHT_BLUE_BUNDLE");
         this.config.set("boards.tokens.title", "Tokens");
         this.config.set("boards.duels.slot", -1);
      }
      if (this.config.getInt("config-version", 0) < 9) {
         this.config.set("refresh-interval-seconds", 120);
         this.config.set("refresh-name", "%color%&lMost %statistic%");
         this.config.set("refresh-lore", List.of(
               "&8Leaderboard",
               "",
               "%color%Information:",
               "%color%| &fRankings refresh in",
               "%color%| &f%time%"
         ));
      }
      this.config.set("refresh-slot", -1);
      this.config.set("board-slots", List.of(
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
      ));
      this.config.set("rows", 5);
      this.config.set("main.head-slot", 13);
      this.config.set("boards.kills.slot", 20);
      this.config.set("boards.kills.material", "NETHERITE_SWORD");
      this.config.set("boards.kills.title", "Kills");
      this.config.set("boards.playtime.slot", 21);
      this.config.set("boards.playtime.material", "CLOCK");
      this.config.set("boards.playtime.title", "Playtime");
      this.config.set("boards.totems.slot", 22);
      this.config.set("boards.totems.material", "TOTEM_OF_UNDYING");
      this.config.set("boards.totems.title", "Totem Pops");
      this.config.set("boards.killstreaks.slot", 23);
      this.config.set("boards.killstreaks.material", "MACE");
      this.config.set("boards.killstreaks.title", "Killstreaks");
      this.config.set("boards.teams.slot", 24);
      this.config.set("boards.teams.material", "PURPLE_BANNER");
      this.config.set("boards.teams.title", "Team");
      this.config.set("boards.deaths.slot", 30);
      this.config.set("boards.deaths.material", "SKELETON_SKULL");
      this.config.set("boards.deaths.title", "Deaths");
      this.config.set("boards.tokens.slot", 32);
      this.config.set("boards.tokens.material", "LIGHT_BLUE_BUNDLE");
      this.config.set("boards.tokens.title", "Tokens");
      this.config.set("boards.duels.slot", -1);
      this.config.set("refresh-interval-seconds", 120);
      this.config.set("hub-refresh-slot", -1);
      this.config.set("hub-refresh-name", "%color%&lREFRESHES IN");
      this.config.set("hub-refresh-lore", List.of(
            "&8Leaderboard",
            "",
            "%color%Information:",
            "%color%| &fRankings refresh in",
            "%color%| &f%time%"
      ));
      this.config.set("config-version", 18);
      if (this.config.getConfigurationSection("position-heads") == null && this.config.get("position-heads") == null) {
         this.config.createSection("position-heads");
      }
      try {
         this.config.save(new java.io.File(this.moduleFolder(), "config.yml"));
      } catch (Exception ignored) {
      }
   }

   @Override
   protected void onDisable() {
      if (this.refreshTask != null) {
         this.refreshTask.cancel();
         this.refreshTask = null;
      }
      this.service = null;
      this.gui = null;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         String s = command.getName().toLowerCase(Locale.ROOT);
         if (s.equals("stats")) {
            if (!player.hasPermission("sharded.stats.use")) {
               this.send(player, "no-permission", new String[0]);
               return true;
            } else {
               UUID uuid = player.getUniqueId();
               if (args.length >= 1) {
                  if (!player.hasPermission("sharded.stats.others")) {
                     this.send(player, "no-permission", new String[0]);
                     return true;
                  }

                  OfflinePlayer offlineplayer = OfflinePlayers.resolve(args[0]);
                  uuid = offlineplayer.getUniqueId();
               }

               this.gui.openStats(player, uuid);
               return true;
            }
         } else {
            player.sendMessage(Text.c("&7Hologram leaderboards use &f/lb&7."));
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private boolean isKnownType(String type) {
      return List.of(
            "tokens",
            "token",
            "kills",
            "kill",
            "deaths",
            "death",
            "killstreaks",
            "killstreak",
            "streak",
            "playtime",
            "time",
            "teams",
            "team",
            "totems",
            "totem",
            "totempops",
            "duels",
            "duels_wins",
            "wins"
         )
         .contains(type);
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player && this.gui != null) {
         this.gui.handleClick(player, event);
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);
      if (s.equals("stats") && args.length == 1 && sender.hasPermission("sharded.stats.others")) {
         return TabCompleteHelper.knownPlayers(args[0]);
      } else {
         return List.of();
      }
   }
}
