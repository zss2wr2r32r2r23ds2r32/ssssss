package com.sharded.core.modules.leaderboardboards;

import com.sharded.core.ShardedCore;
import com.sharded.core.api.leaderboard.Leaderboard;
import com.sharded.core.api.leaderboard.LeaderboardManager;
import com.sharded.core.module.Module;
import com.sharded.core.util.TabCompleteHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;

public final class LeaderboardBoardsModule extends Module implements CommandExecutor, TabCompleter, LeaderboardManager {
   static final String ADMIN = "sharded.leaderboard.admin";
   static final String LEGACY_ADMIN = "sharded.leaderboardtopper.admin";
   private static final Set<String> ADMIN_SUBS = Set.of(
      "create", "delete", "remove", "reload", "setlocation", "setloc", "list", "info", "entries", "movehere", "place", "tp", "purge", "clean", "lbremove"
   );
   private BoardStorage storage;
   private RankingCache cache;
   private HologramRenderer holograms;
   private LineEditor editor;
   private CommandExecutor leaderboardOriginal;
   private TabCompleter leaderboardOriginalTab;

   public LeaderboardBoardsModule(ShardedCore plugin) {
      super(plugin, "leaderboardboards");
   }

   ShardedCore plugin() {
      return this.plugin;
   }

   BoardDefinition board(String id) {
      return this.storage == null ? null : this.storage.get(id);
   }

   @Override
   protected void onEnable() {
      this.ensurePurgeMessages();
      this.ensureRefreshDefaults();
      this.storage = new BoardStorage(this.plugin, this.moduleFolder());
      this.storage.load();
      this.cache = new RankingCache(this.plugin, this.config);
      this.holograms = new HologramRenderer(this.plugin, this.config, this.cache);
      this.editor = new LineEditor(this);
      this.cache.onUpdate(statistic -> {
         for (BoardDefinition boarddefinition1 : this.storage.boards().values()) {
            if (boarddefinition1.statisticKey().equalsIgnoreCase(statistic)) {
               this.holograms.update(boarddefinition1);
            }
         }
      });

      for (BoardDefinition boarddefinition : this.storage.boards().values()) {
         this.cache.ensure(boarddefinition.statisticKey());
      }

      for (String s : List.of("kills", "deaths", "tokens", "playtime", "killstreaks", "elo")) {
         this.cache.ensure(s);
      }

      this.holograms.clearWorld();
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         for (BoardDefinition boarddefinition1 : this.storage.boards().values()) {
            this.holograms.spawn(boarddefinition1);
         }

         this.cache.refreshAll();
      }, 40L);
      this.cache.start();
      Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         if (this.holograms != null && this.storage != null) {
            this.holograms.tick(this.storage.boards().values());
         }
      }, 20L, 20L);
      this.registerCommand("lb", this);
      this.registerCommand("lbsetline", this);
      this.registerCommand("lbremove", this);
      this.registerCommand("lbplace", this);
      this.registerListener(this);
      this.registerListener(this.holograms);
      this.registerListener(this.editor);
      this.wrapLeaderboardCommand();
      this.registerPlaceholders();
   }

   @Override
   protected void onDisable() {
      if (this.cache != null) {
         this.cache.stop();
      }

      if (this.holograms != null) {
         this.holograms.clearWorld();
      }

      this.unwrapLeaderboardCommand();
   }

   public long hologramRefreshRemainingMs() {
      if (this.cache == null) {
         long interval = 120_000L;
         return interval - System.currentTimeMillis() % interval;
      }
      return this.cache.millisUntilRefresh();
   }

   @Override
   public Leaderboard getLeaderboard(String name) {
      if (name != null && this.storage != null) {
         BoardDefinition boarddefinition = this.storage.get(name);
         return boarddefinition == null ? null : new BoardView(boarddefinition, this.cache);
      } else {
         return null;
      }
   }

   @Override
   public Collection<Leaderboard> getLeaderboards() {
      List<Leaderboard> list = new ArrayList<>();
      if (this.storage == null) {
         return list;
      } else {
         for (BoardDefinition boarddefinition : this.storage.boards().values()) {
            list.add(new BoardView(boarddefinition, this.cache));
         }

         return list;
      }
   }

   @Override
   public void refresh(String statisticOrBoard) {
      BoardDefinition boarddefinition = this.storage.get(statisticOrBoard);
      String s = boarddefinition == null ? statisticOrBoard : boarddefinition.statisticKey();
      this.cache.refresh(s);
   }

   @Override
   public void refreshAll() {
      this.cache.refreshAll();
   }

   @Override
   public String placeholder(String params) {
      return this.placeholder(params, null);
   }

   public String placeholder(String params, OfflinePlayer viewer) {
      if (this.cache == null) {
         return "";
      } else if (params != null && params.contains("%")) {
         return PlaceholderParser.resolveLine(params, this.cache, viewer, "---");
      } else {
         PlaceholderParser.Request placeholderparser$request = PlaceholderParser.parse(params);
         return placeholderparser$request == null ? "" : PlaceholderParser.replacement(placeholderparser$request, this.cache, viewer, "---");
      }
   }

   void saveAndRespawn(BoardDefinition board) {
      this.storage.save();
      this.holograms.spawn(board);
   }

   void resetLines(BoardDefinition board) {
      board.title = this.defaultTitle(board.statisticKey());
      board.lines = this.defaultLines(board.statisticKey(), board.entries);
      this.saveAndRespawn(board);
   }

   void msg(CommandSender sender, String key, String... pairs) {
      String[] astring = new String[pairs.length];

      for (int i = 0; i < pairs.length; i++) {
         if (i % 2 == 0 && pairs[i] != null && !pairs[i].startsWith("%")) {
            astring[i] = "%" + pairs[i] + "%";
         } else {
            astring[i] = pairs[i];
         }
      }

      this.send(sender, key, astring);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);
      if (s.equals("lbsetline") || s.equals("setline")) {
         return this.setLine(sender, args);
      } else if (s.equals("lbremove") || s.equals("lbpurge") || s.equals("lbclean")) {
         return this.purge(sender, args);
      } else if (s.equals("lbplace")) {
         String[] astring = new String[args.length + 1];
         astring[0] = "setlocation";
         System.arraycopy(args, 0, astring, 1, args.length);
         return this.setLocation(sender, astring);
      } else {
         return this.admin(sender, args);
      }
   }

   boolean handleLeaderboard(CommandSender sender, String[] args) {
      return args.length != 0 && ADMIN_SUBS.contains(args[0].toLowerCase(Locale.ROOT)) ? this.admin(sender, args) : false;
   }

   private boolean admin(CommandSender sender, String[] args) {
      if (!this.isAdmin(sender)) {
         this.msg(sender, "no-permission");
         return true;
      } else if (args.length == 0) {
         this.msg(sender, "usage");
         return true;
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);

         return switch (s) {
            case "create" -> this.create(sender, args);
            case "delete", "remove" -> this.delete(sender, args);
            case "reload" -> this.reloadCmd(sender);
            case "setlocation", "setloc", "movehere", "place" -> this.setLocation(sender, args);
            case "list" -> this.list(sender);
            case "info" -> this.info(sender, args);
            case "entries" -> this.entries(sender, args);
            case "tp" -> this.teleport(sender, args);
            case "purge", "clean", "lbremove" -> this.purge(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> {
               this.msg(sender, "usage");
               yield true;
            }
         };
      }
   }

   private boolean create(CommandSender sender, String[] args) {
      if (args.length < 3) {
         this.msg(sender, "usage-create");
         return true;
      } else {
         String s = BoardStorage.sanitize(args[1]);
         String s1 = BoardStyles.statistic(args[2]);
         if (s.isEmpty()) {
            this.msg(sender, "invalid-name", "board", args[1]);
            return true;
         } else if (this.storage.get(s) != null) {
            this.msg(sender, "already-exists", "board", s);
            return true;
         } else {
            int i = this.config.getInt("defaults.entries", 10);
            BoardDefinition boarddefinition = this.storage.create(s, s1, this.defaultLines(s1, i), this.defaultTitle(s1));
            if (boarddefinition == null) {
               this.msg(sender, "invalid-name", "board", args[1]);
               return true;
            } else {
               boarddefinition.entries = i;
               this.storage.save();
               this.cache.ensure(boarddefinition.statisticKey());
               this.cache.refresh(boarddefinition.statisticKey());
               this.msg(sender, "created", "board", boarddefinition.id, "stat", boarddefinition.statisticKey());
               return true;
            }
         }
      }
   }

   private boolean delete(CommandSender sender, String[] args) {
      if (args.length < 2) {
         this.msg(sender, "usage-delete");
         return true;
      } else {
         BoardDefinition boarddefinition = this.storage.get(args[1]);
         if (boarddefinition == null) {
            this.msg(sender, "unknown-board", "board", args[1]);
            return true;
         } else {
            this.holograms.remove(boarddefinition.id);
            this.storage.delete(boarddefinition.id);
            this.msg(sender, "deleted", "board", boarddefinition.id);
            return true;
         }
      }
   }

   private boolean purge(CommandSender sender, String[] args) {
      if (!this.isAdmin(sender)) {
         this.msg(sender, "no-permission");
         return true;
      } else {
         String s = args.length == 0 ? "near" : args[0].toLowerCase(Locale.ROOT);
         if (s.equals("all") || s.equals("world") || s.equals("everything")) {
            int k = this.holograms.purgeTagged(true);

            for (BoardDefinition boarddefinition : this.storage.boards().values()) {
               this.holograms.spawn(boarddefinition);
            }

            this.msg(sender, "purged-all", "amount", String.valueOf(k));
            return true;
         } else if (s.equals("orphans") || s.equals("orphan") || s.equals("old")) {
            int j = this.holograms.purgeOrphans(this.storage.boards().keySet());
            this.msg(sender, "purged-orphans", "amount", String.valueOf(j));
            return true;
         } else if (!(sender instanceof Player player)) {
            this.msg(sender, "players-only");
            return true;
         } else {
            double radius = 12.0;
            if (!s.equals("near") && !s.equals("nearby")) {
               try {
                  radius = Double.parseDouble(s);
               } catch (NumberFormatException numberformatexception) {
               }
            } else if (args.length >= 2) {
               try {
                  radius = Double.parseDouble(args[1]);
               } catch (NumberFormatException numberformatexception1) {
               }
            }

            int i = this.holograms.purgeNearby(player.getLocation(), radius);
            this.msg(sender, "purged-near", "amount", String.valueOf(i));
            return true;
         }
      }
   }

   private boolean reloadCmd(CommandSender sender) {
      this.loadConfigs();
      this.storage.load();
      this.holograms.clearWorld();

      for (BoardDefinition boarddefinition : this.storage.boards().values()) {
         this.cache.ensure(boarddefinition.statisticKey());
         this.holograms.spawn(boarddefinition);
      }

      this.cache.refreshAll();
      this.msg(sender, "reloaded");
      return true;
   }

   private boolean setLocation(CommandSender sender, String[] args) {
      if (sender instanceof Player player) {
         if (args.length < 2) {
            this.msg(sender, "usage-setlocation");
            return true;
         } else {
            BoardDefinition boarddefinition = this.storage.get(args[1]);
            if (boarddefinition == null) {
               this.msg(sender, "unknown-board", "board", args[1]);
               return true;
            } else {
               Location location = player.getLocation().clone();
               location.setPitch(0.0F);
               boarddefinition.setLocation(location);
               boarddefinition.hologramEnabled = true;
               this.saveAndRespawn(boarddefinition);
               this.msg(player, "set-location", "board", boarddefinition.id);
               return true;
            }
         }
      } else {
         this.msg(sender, "players-only");
         return true;
      }
   }

   private boolean list(CommandSender sender) {
      if (this.storage.boards().isEmpty()) {
         this.msg(sender, "list-empty");
         return true;
      } else {
         this.msg(sender, "list-header");

         for (BoardDefinition boarddefinition : this.storage.boards().values()) {
            this.msg(
               sender,
               "list-line",
               "board",
               boarddefinition.id,
               "stat",
               boarddefinition.statisticKey(),
               "placed",
               boarddefinition.placed() ? "yes" : "no",
               "entries",
               String.valueOf(boarddefinition.entries)
            );
         }

         return true;
      }
   }

   private boolean info(CommandSender sender, String[] args) {
      if (args.length < 2) {
         this.msg(sender, "usage-info");
         return true;
      } else {
         BoardDefinition boarddefinition = this.storage.get(args[1]);
         if (boarddefinition == null) {
            this.msg(sender, "unknown-board", "board", args[1]);
            return true;
         } else {
            this.msg(
               sender,
               "info",
               "board",
               boarddefinition.id,
               "stat",
               boarddefinition.statisticKey(),
               "entries",
               String.valueOf(boarddefinition.entries),
               "placed",
               boarddefinition.placed()
                  ? boarddefinition.world + " " + (int)boarddefinition.x + " " + (int)boarddefinition.y + " " + (int)boarddefinition.z
                  : "not placed"
            );
            return true;
         }
      }
   }

   private boolean entries(CommandSender sender, String[] args) {
      if (args.length < 3) {
         this.msg(sender, "usage-entries");
         return true;
      } else {
         BoardDefinition boarddefinition = this.storage.get(args[1]);
         if (boarddefinition == null) {
            this.msg(sender, "unknown-board", "board", args[1]);
            return true;
         } else {
            try {
               boarddefinition.entries = Math.max(1, Math.min(20, Integer.parseInt(args[2])));
            } catch (NumberFormatException numberformatexception) {
               this.msg(sender, "usage-entries");
               return true;
            }

            this.saveAndRespawn(boarddefinition);
            this.msg(sender, "entries-set", "board", boarddefinition.id, "entries", String.valueOf(boarddefinition.entries));
            return true;
         }
      }
   }

   private boolean teleport(CommandSender sender, String[] args) {
      if (sender instanceof Player player) {
         if (args.length < 2) {
            this.msg(sender, "usage-tp");
            return true;
         } else {
            BoardDefinition boarddefinition = this.storage.get(args[1]);
            if (boarddefinition != null && boarddefinition.location() != null) {
               player.teleport(boarddefinition.location());
               return true;
            } else {
               this.msg(sender, "unknown-board", "board", args[1]);
               return true;
            }
         }
      } else {
         this.msg(sender, "players-only");
         return true;
      }
   }

   private boolean setLine(CommandSender sender, String[] args) {
      if (!this.isAdmin(sender)) {
         this.msg(sender, "no-permission");
         return true;
      } else if (sender instanceof Player player) {
         if (args.length < 1) {
            this.msg(sender, "usage-setline");
            return true;
         } else {
            BoardDefinition boarddefinition = this.storage.get(args[0]);
            if (boarddefinition == null) {
               this.msg(sender, "unknown-board", "board", args[0]);
               return true;
            } else if (args.length == 1) {
               this.editor.open(player, boarddefinition);
               return true;
            } else if (args[1].equalsIgnoreCase("add")) {
               String s1 = args.length < 3 ? "" : String.join(" ", Arrays.copyOfRange(args, 2, args.length));
               boarddefinition.lines.add(s1);
               this.saveAndRespawn(boarddefinition);
               this.msg(player, "line-added", "board", boarddefinition.id);
               return true;
            } else {
               try {
                  int i = Integer.parseInt(args[1]) - 1;
                  if (i < 0) {
                     throw new NumberFormatException();
                  } else {
                     String s = args.length < 3 ? "" : String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                     List<String> list = new ArrayList<>();
                     if (boarddefinition.title != null && !boarddefinition.title.isBlank()) {
                        list.add(boarddefinition.title);
                     }

                     list.addAll(boarddefinition.lines);

                     while (list.size() <= i) {
                        list.add("");
                     }

                     list.set(i, s);
                     if (!boarddefinition.title.isBlank() && !list.isEmpty()) {
                        boarddefinition.title = list.getFirst();
                        boarddefinition.lines = new ArrayList<>(list.subList(1, list.size()));
                     } else {
                        boarddefinition.title = "";
                        boarddefinition.lines = list;
                     }

                     this.saveAndRespawn(boarddefinition);
                     this.msg(player, "line-set", "board", boarddefinition.id, "line", String.valueOf(i + 1));
                     return true;
                  }
               } catch (NumberFormatException numberformatexception) {
                  this.editor.open(player, boarddefinition);
                  return true;
               }
            }
         }
      } else {
         this.msg(sender, "players-only");
         return true;
      }
   }

   @EventHandler
   public void onChunkLoad(ChunkLoadEvent event) {
      if (this.storage != null) {
         for (BoardDefinition boarddefinition : this.storage.boards().values()) {
            this.holograms.ensure(boarddefinition, event.getChunk());
         }
      }
   }

   @EventHandler
   public void onWorldLoad(WorldLoadEvent event) {
      if (this.storage != null) {
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            for (BoardDefinition boarddefinition : this.storage.boards().values()) {
               if (event.getWorld().getName().equals(boarddefinition.world)) {
                  this.holograms.spawn(boarddefinition);
               }
            }
         });
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);
      if (!this.isAdmin(sender)) {
         return List.of();
      } else {
         List<String> list = (List<String>)(this.storage == null ? List.of() : new ArrayList<>(this.storage.boards().keySet()));
         if (s.equals("lbremove") || s.equals("lbpurge") || s.equals("lbclean")) {
            return args.length == 1 ? TabCompleteHelper.filter(args[0], List.of("near", "all", "orphans")) : List.of();
         } else if (s.equals("lbplace")) {
            return args.length == 1 ? TabCompleteHelper.filter(args[0], list) : List.of();
         } else if (!s.equals("lbsetline") && !s.equals("setline")) {
            if (args.length == 1) {
               return TabCompleteHelper.filter(args[0], List.of("create", "delete", "reload", "setlocation", "list", "info", "entries", "tp", "purge", "place"));
            } else if (args.length != 2 || !args[0].equalsIgnoreCase("purge") && !args[0].equalsIgnoreCase("clean")) {
               if (args.length == 2 && !args[0].equalsIgnoreCase("create") && !args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("list")) {
                  return TabCompleteHelper.filter(args[1], list);
               } else {
                  return args.length == 3 && args[0].equalsIgnoreCase("create")
                     ? TabCompleteHelper.filter(args[2], List.of("kills", "killstreaks", "deaths", "tokens", "playtime", "elo", "duels"))
                     : List.of();
               }
            } else {
               return TabCompleteHelper.filter(args[1], List.of("near", "all", "orphans"));
            }
         } else {
            return args.length == 1 ? TabCompleteHelper.filter(args[0], list) : List.of();
         }
      }
   }

   List<String> adminFirstArgTabs() {
      return List.of("create", "delete", "reload", "setlocation", "list", "purge", "place");
   }

   boolean isAdmin(CommandSender sender) {
      return sender.isOp() || sender.hasPermission("sharded.leaderboard.admin") || sender.hasPermission("sharded.leaderboardtopper.admin");
   }

   private void ensureRefreshDefaults() {
      boolean flag = false;
      if (this.config.getInt("config-version", 0) < 4) {
         this.config.set("refresh-seconds", 120);
         this.config.set("cache-size", Math.max(100, this.config.getInt("cache-size", 20)));
         this.config.set("config-version", 4);
         flag = true;
      }

      if (flag) {
         try {
            this.config.save(new File(this.moduleFolder(), "config.yml"));
         } catch (Exception exception) {
         }
      }
   }

   private void ensurePurgeMessages() {
      File file1 = new File(this.moduleFolder(), "messages.yml");
      YamlConfiguration yamlconfiguration = file1.isFile() ? YamlConfiguration.loadConfiguration(file1) : new YamlConfiguration();
      boolean flag = false;
      flag |= this.putMessage(yamlconfiguration, "usage-purge", "&#FF007B&lLEADERBOARD &8▷ &fUse /lbremove [near|all|orphans]");
      flag |= this.putMessage(
         yamlconfiguration, "purged-near", "&#FF007B&lLEADERBOARD &8▷ &fRemoved &#FF007B%amount% &fold hologram(s) near you. Crate holograms were left alone."
      );
      flag |= this.putMessage(
         yamlconfiguration, "purged-all", "&#FF007B&lLEADERBOARD &8▷ &fRemoved &#FF007B%amount% &fold hologram(s) and respawned the current boards."
      );
      flag |= this.putMessage(
         yamlconfiguration, "purged-orphans", "&#FF007B&lLEADERBOARD &8▷ &fRemoved &#FF007B%amount% &forphan / old leaderboard hologram(s)."
      );
      if (flag) {
         try {
            if (file1.getParentFile() != null) {
               file1.getParentFile().mkdirs();
            }

            yamlconfiguration.save(file1);
         } catch (Exception exception) {
         }

         this.loadConfigs();
      }
   }

   private boolean putMessage(YamlConfiguration yaml, String key, String value) {
      if (yaml.contains(key)) {
         return false;
      } else {
         yaml.set(key, value);
         return true;
      }
   }

   private void wrapLeaderboardCommand() {
      Bukkit.getScheduler()
         .runTask(
            this.plugin,
            () -> {
               for (String s : List.of("leaderboard", "leaderboards")) {
                  PluginCommand plugincommand = this.plugin.getCommand(s);
                  if (plugincommand != null) {
                     this.leaderboardOriginal = plugincommand.getExecutor();
                     this.leaderboardOriginalTab = plugincommand.getTabCompleter();
                     plugincommand.setExecutor(
                        (sender, cmd, label, args) -> this.handleLeaderboard(sender, args)
                              ? true
                              : this.leaderboardOriginal != null && this.leaderboardOriginal.onCommand(sender, cmd, label, args)
                     );
                     plugincommand.setTabCompleter((sender, cmd, label, args) -> {
                        List<String> list = new ArrayList<>();
                        if (this.isAdmin(sender) && args.length == 1) {
                           list.addAll(TabCompleteHelper.filter(args[0], this.adminFirstArgTabs()));
                        }

                        List<String> list1 = this.leaderboardOriginalTab == null
                           ? List.of()
                           : this.leaderboardOriginalTab.onTabComplete(sender, cmd, label, args);
                        if (list1 != null) {
                           list.addAll(list1);
                        }

                        return list;
                     });
                  }
               }
            }
         );
   }

   private void unwrapLeaderboardCommand() {
      for (String s : List.of("leaderboard", "leaderboards")) {
         PluginCommand plugincommand = this.plugin.getCommand(s);
         if (plugincommand != null && this.leaderboardOriginal != null) {
            plugincommand.setExecutor(this.leaderboardOriginal);
            plugincommand.setTabCompleter(this.leaderboardOriginalTab);
         }
      }
   }

   private void registerPlaceholders() {
      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
         try {
            new LeaderboardBoardsModule.Expansion(this).register();
            this.plugin.getLogger().info("[leaderboards] Registered %leaderboard_<stat>_name_1% placeholders.");
         } catch (Throwable throwable) {
            this.plugin.getLogger().warning("[leaderboards] Could not register placeholders: " + throwable.getMessage());
         }
      }
   }

   String defaultTitle(String stat) {
      return BoardStyles.title(stat);
   }

   List<String> defaultLines(String stat, int entries) {
      return BoardStyles.lines(stat, entries);
   }

   private static final class Expansion extends PlaceholderExpansion {
      private final LeaderboardBoardsModule module;

      private Expansion(LeaderboardBoardsModule module) {
         this.module = module;
      }

      public String getIdentifier() {
         return "leaderboard";
      }

      public String getAuthor() {
         return "Sharded";
      }

      public String getVersion() {
         return this.module.plugin.getPluginMeta().getVersion();
      }

      public boolean persist() {
         return true;
      }

      public String onRequest(OfflinePlayer player, String params) {
         return this.module.placeholder(params, player);
      }
   }
}
