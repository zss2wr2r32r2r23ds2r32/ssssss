package com.sharded.core.modules.duel;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.NamespacedKey;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class DuelModule extends Module implements CommandExecutor, TabCompleter {
   private static final List<String> SUBCOMMANDS = List.of(
      "challenge", "accept", "deny", "elo", "top", "leave", "queue", "elotop", "stats", "spectate", "arena"
   );
   private final Map<UUID, DuelModule.DuelRequest> requests = new HashMap<>();
   private final Map<UUID, DuelModule.ActiveDuel> activeDuels = new HashMap<>();
   private final Map<UUID, UUID> queue = new LinkedHashMap<>();
   private final Map<String, Integer> recentPairWins = new HashMap<>();
   private final Map<UUID, Integer> killstreaks = new HashMap<>();
   private final Map<UUID, UUID> spectators = new HashMap<>();
   private Set<Material> bannedMaterials = Set.of(Material.MACE);
   private DuelModule.EloRepository elo;
   private String lastArenaEdit;
   private NamespacedKey requestClick;

   public DuelModule(ShardedCore plugin) {
      super(plugin, "duel");
   }

   @Override
   protected void onEnable() {
      this.loadBannedMaterials();

      try {
         this.elo = new DuelModule.EloRepository(
            this.moduleFolder(), Math.max(0, this.config.getInt("elo.start", 1000)), Math.max(1, this.config.getInt("elo.k-factor", 32))
         );
      } catch (SQLException sqlexception) {
         throw new IllegalStateException("Could not open duel Elo database", sqlexception);
      }

      this.ensureRequestGui();
      this.requestClick = new NamespacedKey(this.plugin, "duel-request");
      this.registerCommand("duels", this);
      this.registerCommand("duel", this);
      this.registerCommand("queue", this);
      this.registerCommand("elo", this);
      this.registerCommand("elotop", this);
      this.registerListener(this);
   }

   public int rating(UUID uuid, String name) {
      if (this.elo == null) {
         return this.config.getInt("elo.start", 1000);
      } else {
         try {
            return this.elo.get(uuid, name).rating();
         } catch (SQLException sqlexception) {
            return this.config.getInt("elo.start", 1000);
         }
      }
   }

   public List<DuelModule.DuelWinEntry> topWins(int limit) {
      if (this.elo == null) {
         return List.of();
      } else {
         try {
            List<DuelModule.DuelWinEntry> list = new ArrayList<>();

            for (DuelModule.EloEntry duelmodule$eloentry : this.elo.topByWins(Math.max(1, limit))) {
               if (duelmodule$eloentry.wins() > 0) {
                  list.add(new DuelModule.DuelWinEntry(duelmodule$eloentry.uuid(), duelmodule$eloentry.name(), duelmodule$eloentry.wins()));
               }
            }

            return list;
         } catch (SQLException sqlexception) {
            this.plugin.getLogger().warning("[duel] Could not read wins leaderboard: " + sqlexception.getMessage());
            return List.of();
         }
      }
   }

   public int winsOf(UUID uuid) {
      if (this.elo != null && uuid != null) {
         try {
            DuelModule.EloEntry duelmodule$eloentry = this.elo.findOrNull(uuid);
            return duelmodule$eloentry == null ? 0 : duelmodule$eloentry.wins();
         } catch (SQLException sqlexception) {
            return 0;
         }
      } else {
         return 0;
      }
   }

   public List<DuelModule.DuelEloEntry> topElo(int limit) {
      if (this.elo == null) {
         return List.of();
      } else {
         try {
            List<DuelModule.DuelEloEntry> list = new ArrayList<>();

            for (DuelModule.EloEntry duelmodule$eloentry : this.elo.top(Math.max(1, limit))) {
               list.add(
                  new DuelModule.DuelEloEntry(
                     duelmodule$eloentry.uuid(), duelmodule$eloentry.name(), duelmodule$eloentry.rating(), this.rankFor(duelmodule$eloentry.rating())
                  )
               );
            }

            return list;
         } catch (SQLException sqlexception) {
            this.plugin.getLogger().warning("[duel] Could not read Elo leaderboard: " + sqlexception.getMessage());
            return List.of();
         }
      }
   }

   public int eloOf(UUID uuid) {
      if (this.elo != null && uuid != null) {
         try {
            DuelModule.EloEntry duelmodule$eloentry = this.elo.findOrNull(uuid);
            return duelmodule$eloentry == null ? this.config.getInt("elo.start", 1000) : duelmodule$eloentry.rating();
         } catch (SQLException sqlexception) {
            return this.config.getInt("elo.start", 1000);
         }
      } else {
         return this.config.getInt("elo.start", 1000);
      }
   }

   public String rankFor(int rating) {
      if (rating >= 4000) {
         return "Legend";
      } else if (rating >= 3000) {
         return "Champion";
      } else if (rating >= 2400) {
         return "Meister";
      } else if (rating >= 2000) {
         return "Diamant";
      } else if (rating >= 1600) {
         return "Platin";
      } else if (rating >= 1300) {
         return "Gold";
      } else {
         return rating >= 1100 ? "Silber" : "Bronze";
      }
   }

   @Override
   protected void onDisable() {
      for (DuelModule.ActiveDuel duelmodule$activeduel : new HashSet<>(this.activeDuels.values())) {
         duelmodule$activeduel.cancelCountdown();
      }

      this.activeDuels.clear();
      this.requests.clear();
      if (this.elo != null) {
         this.elo.close();
         this.elo = null;
      }
   }

   private void loadBannedMaterials() {
      Set<Material> set = new HashSet<>();
      set.add(Material.MACE);

      for (String s : this.config.getStringList("banned-materials")) {
         Material material = Material.matchMaterial(s);
         if (material == null) {
            this.plugin.getLogger().warning("[duel] Unknown banned material: " + s);
         } else {
            set.add(material);
         }
      }

      this.bannedMaterials = Set.copyOf(set);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.duel.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            this.pruneExpiredRequests();
            String s = command.getName().toLowerCase(Locale.ROOT);
            if (s.equals("elo")) {
               return this.showElo(player, args.length == 0 ? new String[]{"elo"} : new String[]{"elo", args[0]});
            } else if (s.equals("elotop")) {
               return this.showTop(player);
            } else if (!s.equals("queue") && (args.length <= 0 || !args[0].equalsIgnoreCase("queue"))) {
               if (args.length == 0) {
                  return this.queue(player);
               } else if (args[0].equalsIgnoreCase("accept") && args.length >= 2) {
                  return this.openRequestGui(player, args[1], true);
               } else if (!SUBCOMMANDS.contains(args[0].toLowerCase(Locale.ROOT)) && Bukkit.getPlayerExact(args[0]) != null) {
                  return this.openRequestGui(player, args[0], false);
               } else {
                  String s1 = args[0].toLowerCase(Locale.ROOT);

                  return switch (s1) {
                     case "challenge" -> this.challenge(player, args);
                     case "accept" -> this.accept(player);
                     case "deny" -> this.deny(player);
                     case "elo", "stats" -> this.showElo(player, args);
                     case "top", "elotop" -> this.showTop(player);
                     case "leave" -> this.leave(player);
                     case "queue" -> this.queue(player);
                     case "spectate" -> this.spectate(player, args);
                     case "arena" -> this.arenaCommand(player, args);
                     default -> {
                        this.send(player, "usage", new String[0]);
                        yield true;
                     }
                  };
               }
            } else {
               return this.queue(player);
            }
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   public void joinQueuePublic(Player player) {
      if (!player.hasPermission("sharded.duel.use")) {
         this.send(player, "no-permission", new String[0]);
      } else {
         this.pruneExpiredRequests();
         this.queue(player);
      }
   }

   private boolean queue(Player player) {
      if (this.activeDuels.containsKey(player.getUniqueId())) {
         this.send(player, "already-dueling", new String[0]);
         return true;
      } else {
         Material material = this.firstBannedMaterial(player);
         if (material != null) {
            this.send(player, "banned-item", new String[]{"%material%", this.prettyMaterial(material)});
            return true;
         } else if (this.queue.containsKey(player.getUniqueId())) {
            this.queue.remove(player.getUniqueId());
            this.send(player, "queue-left", new String[0]);
            return true;
         } else {
            for (Entry<UUID, UUID> entry : new ArrayList<>(this.queue.entrySet())) {
               Player playerx = Bukkit.getPlayer(entry.getKey());
               if (playerx != null && playerx.isOnline() && !playerx.getUniqueId().equals(player.getUniqueId())) {
                  this.queue.remove(entry.getKey());
                  this.removeRequestsInvolving(player.getUniqueId());
                  this.removeRequestsInvolving(playerx.getUniqueId());
                  DuelModule.ActiveDuel duelmodule$activeduel = new DuelModule.ActiveDuel(playerx.getUniqueId(), player.getUniqueId());
                  this.activeDuels.put(playerx.getUniqueId(), duelmodule$activeduel);
                  this.activeDuels.put(player.getUniqueId(), duelmodule$activeduel);
                  this.teleportToArena(playerx, player);
                  this.send(playerx, "queue-matched", new String[]{"%player%", player.getName()});
                  this.send(player, "queue-matched", new String[]{"%player%", playerx.getName()});
                  this.startCountdown(duelmodule$activeduel);
                  return true;
               }

               this.queue.remove(entry.getKey());
            }

            this.queue.put(player.getUniqueId(), player.getUniqueId());
            this.send(player, "queue-joined", new String[0]);
            return true;
         }
      }
   }

   private void teleportToArena(Player first, Player second) {
      DuelModule.ActiveDuel duelmodule$activeduel = this.activeDuels.get(first.getUniqueId());
      if (duelmodule$activeduel != null) {
         duelmodule$activeduel.returnFirst = first.getLocation().clone();
         duelmodule$activeduel.returnSecond = second.getLocation().clone();
      }

      DuelModule.ArenaSpawns duelmodule$arenaspawns = this.pickArena();
      if (duelmodule$arenaspawns != null) {
         if (duelmodule$activeduel != null) {
            duelmodule$activeduel.arenaName = duelmodule$arenaspawns.name();
         }

         first.teleport(duelmodule$arenaspawns.pos1());
         second.teleport(duelmodule$arenaspawns.pos2());
      }
   }

   private DuelModule.ArenaSpawns pickArena() {
      List<DuelModule.ArenaSpawns> list = new ArrayList<>();
      ConfigurationSection configurationsection = this.config.getConfigurationSection("arenas");
      if (configurationsection != null) {
         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            if (configurationsection1 != null && configurationsection1.getBoolean("enabled", true)) {
               Location location = this.readLocation("arenas." + s + ".pos1");
               Location location1 = this.readLocation("arenas." + s + ".pos2");
               if (location != null && location1 != null) {
                  list.add(new DuelModule.ArenaSpawns(s, location, location1));
               }
            }
         }
      }

      if (list.isEmpty() && this.config.getBoolean("arena.enabled", false)) {
         Location location2 = this.readLocation("arena.pos1");
         Location location3 = this.readLocation("arena.pos2");
         if (location2 != null && location3 != null) {
            list.add(new DuelModule.ArenaSpawns("default", location2, location3));
         }
      }

      return list.isEmpty() ? null : list.get(ThreadLocalRandom.current().nextInt(list.size()));
   }

   private boolean arenaCommand(Player player, String[] args) {
      if (!player.hasPermission("sharded.duel.admin") && !player.isOp()) {
         this.send(player, "no-permission", new String[0]);
         return true;
      } else if (args.length < 2) {
         this.send(player, "arena-usage", new String[0]);
         return true;
      } else {
         String s = args[1].toLowerCase(Locale.ROOT);

         return switch (s) {
            case "create" -> {
               if (args.length < 3) {
                  this.send(player, "arena-usage", new String[0]);
                  yield true;
               } else {
                  String s6 = args[2].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
                  if (s6.isBlank()) {
                     this.send(player, "arena-invalid", new String[0]);
                     yield true;
                  } else if (this.config.getConfigurationSection("arenas." + s6) != null) {
                     this.send(player, "arena-exists", new String[]{"%arena%", s6});
                     yield true;
                  } else {
                     this.config.set("arenas." + s6 + ".enabled", true);
                     this.saveConfigFile();
                     this.lastArenaEdit = s6;
                     this.send(player, "arena-created", new String[]{"%arena%", s6});
                     yield true;
                  }
               }
            }
            case "setspawn1", "spawn1", "pos1" -> {
               String s5 = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : this.lastArenaEdit;
               yield this.setArenaSpawn(player, s5, "pos1");
            }
            case "setspawn2", "spawn2", "pos2" -> {
               String s4 = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : this.lastArenaEdit;
               yield this.setArenaSpawn(player, s4, "pos2");
            }
            case "delete", "remove" -> {
               if (args.length < 3) {
                  this.send(player, "arena-usage", new String[0]);
                  yield true;
               } else {
                  String s3 = args[2].toLowerCase(Locale.ROOT);
                  if (this.config.getConfigurationSection("arenas." + s3) == null) {
                     this.send(player, "arena-missing", new String[]{"%arena%", s3});
                     yield true;
                  } else {
                     this.config.set("arenas." + s3, null);
                     this.saveConfigFile();
                     this.send(player, "arena-deleted", new String[]{"%arena%", s3});
                     yield true;
                  }
               }
            }
            case "list" -> {
               ConfigurationSection configurationsection1 = this.config.getConfigurationSection("arenas");
               this.send(player, "arena-list-header", new String[0]);
               if (configurationsection1 != null && !configurationsection1.getKeys(false).isEmpty()) {
                  for (String s2 : configurationsection1.getKeys(false)) {
                     ConfigurationSection configurationsection = configurationsection1.getConfigurationSection(s2);
                     boolean flag1 = configurationsection != null && configurationsection.getBoolean("enabled", true);
                     boolean flag2 = this.readLocation("arenas." + s2 + ".pos1") != null && this.readLocation("arenas." + s2 + ".pos2") != null;
                     this.send(player, "arena-list-line", new String[]{"%arena%", s2, "%status%", flag1 ? (flag2 ? "ready" : "incomplete") : "disabled"});
                  }

                  yield true;
               } else {
                  this.send(player, "arena-list-empty", new String[0]);
                  yield true;
               }
            }
            case "enable", "disable" -> {
               if (args.length < 3) {
                  this.send(player, "arena-usage", new String[0]);
                  yield true;
               } else {
                  String s1 = args[2].toLowerCase(Locale.ROOT);
                  if (this.config.getConfigurationSection("arenas." + s1) == null) {
                     this.send(player, "arena-missing", new String[]{"%arena%", s1});
                     yield true;
                  } else {
                     boolean flag = s.equals("enable");
                     this.config.set("arenas." + s1 + ".enabled", flag);
                     this.saveConfigFile();
                     this.send(player, flag ? "arena-enabled" : "arena-disabled", new String[]{"%arena%", s1});
                     yield true;
                  }
               }
            }
            default -> {
               this.send(player, "arena-usage", new String[0]);
               yield true;
            }
         };
      }
   }

   private boolean setArenaSpawn(Player player, String id, String posKey) {
      if (id != null && !id.isBlank()) {
         id = id.toLowerCase(Locale.ROOT);
         if (this.config.getConfigurationSection("arenas." + id) == null) {
            this.config.set("arenas." + id + ".enabled", true);
         }

         Location location = player.getLocation();
         String s = "arenas." + id + "." + posKey;
         this.config.set(s + ".world", location.getWorld() == null ? "world" : location.getWorld().getName());
         this.config.set(s + ".x", location.getX());
         this.config.set(s + ".y", location.getY());
         this.config.set(s + ".z", location.getZ());
         this.config.set(s + ".yaw", location.getYaw());
         this.config.set(s + ".pitch", location.getPitch());
         this.saveConfigFile();
         this.lastArenaEdit = id;
         this.send(player, "arena-spawn-set", new String[]{"%arena%", id, "%spawn%", posKey.equals("pos1") ? "1" : "2"});
         return true;
      } else {
         this.send(player, "arena-usage", new String[0]);
         return true;
      }
   }

   private boolean spectate(Player player, String[] args) {
      if (args.length < 2) {
         this.send(player, "spectate-usage", new String[0]);
         return true;
      } else if (this.activeDuels.containsKey(player.getUniqueId())) {
         this.send(player, "already-dueling", new String[0]);
         return true;
      } else {
         Player playerx = Bukkit.getPlayerExact(args[1]);
         if (playerx != null && playerx.isOnline()) {
            DuelModule.ActiveDuel duelmodule$activeduel = this.activeDuels.get(playerx.getUniqueId());
            if (duelmodule$activeduel == null) {
               this.send(player, "spectate-not-dueling", new String[]{"%player%", playerx.getName()});
               return true;
            } else {
               this.spectators.put(player.getUniqueId(), playerx.getUniqueId());
               player.setGameMode(GameMode.SPECTATOR);
               player.teleport(playerx.getLocation());
               this.send(player, "spectate-started", new String[]{"%player%", playerx.getName()});
               return true;
            }
         } else {
            this.send(player, "player-not-found", new String[]{"%player%", args[1]});
            return true;
         }
      }
   }

   private void clearSpectators(DuelModule.ActiveDuel duel) {
      List<UUID> list = new ArrayList<>();

      for (Entry<UUID, UUID> entry : this.spectators.entrySet()) {
         if (entry.getValue().equals(duel.first) || entry.getValue().equals(duel.second)) {
            list.add(entry.getKey());
         }
      }

      for (UUID uuid : list) {
         this.spectators.remove(uuid);
         Player player = Bukkit.getPlayer(uuid);
         if (player != null && player.isOnline()) {
            player.setGameMode(GameMode.SURVIVAL);
            this.send(player, "spectate-ended", new String[0]);
         }
      }
   }

   private void returnPlayers(DuelModule.ActiveDuel duel) {
      if (this.config.getBoolean("return-after-duel", true)) {
         Player player = Bukkit.getPlayer(duel.first);
         Player player1 = Bukkit.getPlayer(duel.second);
         if (player != null && duel.returnFirst != null) {
            player.teleport(duel.returnFirst);
         }

         if (player1 != null && duel.returnSecond != null) {
            player1.teleport(duel.returnSecond);
         }
      }
   }

   private void runMatchEndCommands(Player winner, Player loser) {
      for (String s : this.config.getStringList("match-end-commands.winner")) {
         if (s != null && !s.isBlank()) {
            Bukkit.dispatchCommand(
               Bukkit.getConsoleSender(), s.replace("%player%", winner.getName()).replace("%winner%", winner.getName()).replace("%loser%", loser.getName())
            );
         }
      }

      for (String s1 : this.config.getStringList("match-end-commands.loser")) {
         if (s1 != null && !s1.isBlank()) {
            Bukkit.dispatchCommand(
               Bukkit.getConsoleSender(), s1.replace("%player%", loser.getName()).replace("%winner%", winner.getName()).replace("%loser%", loser.getName())
            );
         }
      }
   }

   private Location readLocation(String path) {
      String s = this.config.getString(path + ".world");
      if (s != null && !s.isBlank()) {
         World world = Bukkit.getWorld(s);
         return world == null
            ? null
            : new Location(
               world,
               this.config.getDouble(path + ".x"),
               this.config.getDouble(path + ".y"),
               this.config.getDouble(path + ".z"),
               (float)this.config.getDouble(path + ".yaw", 0.0),
               (float)this.config.getDouble(path + ".pitch", 0.0)
            );
      } else {
         return null;
      }
   }

   private void ensureRequestGui() {
      if (this.config.isConfigurationSection("request-gui.buttons.send")) {
         return;
      }
      try (InputStream input = this.plugin.getResource(this.jarResourcePath("config.yml"))) {
         if (input == null) {
            return;
         }
         YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
         ConfigurationSection section = bundled.getConfigurationSection("request-gui");
         if (section != null) {
            this.config.set("request-gui", section);
            this.saveConfigFile();
         }
      } catch (Exception exception) {
         this.plugin.getLogger().warning("[duel] Could not add request GUI defaults: " + exception.getMessage());
      }
   }

   private boolean openRequestGui(Player viewer, String otherName, boolean confirm) {
      Player other = Bukkit.getPlayerExact(otherName);
      if (other == null || !other.isOnline()) {
         this.send(viewer, "player-not-found", new String[]{"%player%", otherName});
         return true;
      }
      if (confirm) {
         DuelModule.DuelRequest request = this.requests.get(viewer.getUniqueId());
         if (request == null || request.expired() || !request.challenger().equals(other.getUniqueId())) {
            this.send(viewer, "no-request", new String[0]);
            return true;
         }
      }
      ConfigurationSection gui = this.config.getConfigurationSection("request-gui");
      int rows = gui == null ? 3 : Math.max(1, Math.min(6, gui.getInt("rows", 3)));
      DuelModule.RequestHolder holder = new DuelModule.RequestHolder();
      holder.subject = other.getUniqueId();
      holder.confirm = confirm;
      Inventory inventory = Bukkit.createInventory(holder, rows * 9, Text.c(gui == null ? "&8Duel" : gui.getString("title", "&8Duel")));
      holder.inventory = inventory;
      Material fillerMat = Material.BLACK_STAINED_GLASS_PANE;
      if (gui != null) {
         Material parsed = Material.matchMaterial(gui.getString("filler-material", "BLACK_STAINED_GLASS_PANE"));
         if (parsed != null) {
            fillerMat = parsed;
         }
      }
      ItemStack filler = this.requestButton(fillerMat, gui == null ? " " : gui.getString("filler-name", " "), List.of(), null);
      for (int i = 0; i < inventory.getSize(); i++) {
         inventory.setItem(i, filler.clone());
      }
      String world = this.worldLabel(other.getWorld() == null ? "" : other.getWorld().getName());
      String name = other.getName();
      String elo = String.valueOf(this.eloOf(other.getUniqueId()));
      this.placeRequestButton(inventory, gui, "cancel", 10, Material.RED_STAINED_GLASS_PANE, "cancel", name, world, elo);
      this.placeRequestButton(inventory, gui, "world", 12, Material.SPYGLASS, null, name, world, elo);
      this.placeRequestHead(inventory, gui, other, name, world, elo);
      this.placeRequestButton(inventory, gui, "elo", 14, Material.HEAVY_CORE, null, name, world, elo);
      this.placeRequestButton(inventory, gui, confirm ? "confirm" : "send", 16, Material.LIME_STAINED_GLASS_PANE, confirm ? "confirm" : "send", name, world, elo);
      viewer.openInventory(inventory);
      return true;
   }

   private void placeRequestButton(
      Inventory inventory, ConfigurationSection gui, String key, int fallbackSlot, Material fallback, String action, String player, String world, String elo
   ) {
      ConfigurationSection section = gui == null ? null : gui.getConfigurationSection("buttons." + key);
      int slot = section == null ? fallbackSlot : section.getInt("slot", fallbackSlot);
      Material material = fallback;
      if (section != null) {
         Material parsed = Material.matchMaterial(section.getString("material", fallback.name()));
         if (parsed != null) {
            material = parsed;
         }
      }
      String name = this.fillRequest(section == null ? fallback.name() : section.getString("name", fallback.name()), player, world, elo);
      List<String> lore = new ArrayList<>();
      List<String> lines = section == null ? List.of() : section.getStringList("lore");
      for (String line : lines) {
         lore.add(this.fillRequest(line, player, world, elo));
      }
      if (slot >= 0 && slot < inventory.getSize()) {
         inventory.setItem(slot, this.requestButton(material, name, lore, action));
      }
   }

   private void placeRequestHead(Inventory inventory, ConfigurationSection gui, Player other, String player, String world, String elo) {
      ConfigurationSection section = gui == null ? null : gui.getConfigurationSection("buttons.head");
      int slot = section == null ? 13 : section.getInt("slot", 13);
      String name = this.fillRequest(section == null ? "&#ff0067%player%" : section.getString("name", "&#ff0067%player%"), player, world, elo);
      List<String> lore = new ArrayList<>();
      List<String> lines = section == null ? List.of() : section.getStringList("lore");
      for (String line : lines) {
         lore.add(this.fillRequest(line, player, world, elo));
      }
      ItemStack head = new ItemStack(Material.PLAYER_HEAD);
      if (head.getItemMeta() instanceof SkullMeta skull) {
         skull.setOwningPlayer(other);
         skull.displayName(Text.c(name));
         skull.itemName(null);
         List<net.kyori.adventure.text.Component> components = new ArrayList<>();
         for (String line : lore) {
            components.add(Text.c(line));
         }
         skull.lore(components);
         head.setItemMeta(skull);
      }
      if (slot >= 0 && slot < inventory.getSize()) {
         inventory.setItem(slot, head);
      }
   }

   private ItemStack requestButton(Material material, String name, List<String> lore, String action) {
      ItemStack item = new ItemBuilder(material).name(name).lore(lore).build();
      if (action != null && this.requestClick != null && item.getItemMeta() != null) {
         item.editMeta(meta -> meta.getPersistentDataContainer().set(this.requestClick, PersistentDataType.STRING, action));
      }
      return item;
   }

   private String fillRequest(String line, String player, String world, String elo) {
      if (line == null) {
         return "";
      }
      return line.replace("%player%", player == null ? "" : player).replace("%world%", world == null ? "" : world).replace("%elo%", elo == null ? "" : elo);
   }

   private String worldLabel(String world) {
      if (world == null || world.isBlank()) {
         return "";
      }
      String mapped = this.config.getString("request-gui.world-names." + world);
      if (mapped != null && !mapped.isBlank()) {
         return mapped;
      }
      return switch (world.toLowerCase(Locale.ROOT)) {
         case "world_nether" -> "Nether";
         case "world_the_end" -> "End";
         default -> world;
      };
   }

   @EventHandler
   public void onRequestClick(InventoryClickEvent event) {
      if (!(event.getInventory().getHolder() instanceof DuelModule.RequestHolder holder)) {
         return;
      }
      event.setCancelled(true);
      if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null || this.requestClick == null || event.getCurrentItem().getItemMeta() == null) {
         return;
      }
      String action = event.getCurrentItem().getItemMeta().getPersistentDataContainer().get(this.requestClick, PersistentDataType.STRING);
      if (action == null) {
         return;
      }
      player.closeInventory();
      if (action.equals("send")) {
         Player target = Bukkit.getPlayer(holder.subject);
         if (target != null) {
            this.challenge(player, new String[]{"challenge", target.getName()});
         }
      } else if (action.equals("confirm")) {
         this.accept(player);
      }
   }

   private boolean challenge(Player challenger, String[] args) {
      if (args.length != 2) {
         this.send(challenger, "challenge-usage", new String[0]);
         return true;
      } else {
         Player player = Bukkit.getPlayerExact(args[1]);
         if (player != null && player.isOnline()) {
            if (player.getUniqueId().equals(challenger.getUniqueId())) {
               this.send(challenger, "challenge-self", new String[0]);
               return true;
            } else if (this.activeDuels.containsKey(challenger.getUniqueId())) {
               this.send(challenger, "already-dueling", new String[0]);
               return true;
            } else if (this.activeDuels.containsKey(player.getUniqueId())) {
               this.send(challenger, "target-dueling", new String[]{"%player%", player.getName()});
               return true;
            } else if (this.hasPendingRequest(challenger.getUniqueId())) {
               this.send(challenger, "request-already-pending", new String[0]);
               return true;
            } else if (this.hasPendingRequest(player.getUniqueId())) {
               this.send(challenger, "target-request-pending", new String[]{"%player%", player.getName()});
               return true;
            } else {
               long i = System.currentTimeMillis() + Math.max(1L, this.config.getLong("requests-expire-seconds", 60L)) * 1000L;
               this.requests.put(player.getUniqueId(), new DuelModule.DuelRequest(challenger.getUniqueId(), player.getUniqueId(), i));
               this.send(challenger, "request-sent", new String[]{"%player%", player.getName()});
               this.send(player, "request-received", new String[]{"%player%", challenger.getName()});
               return true;
            }
         } else {
            this.send(challenger, "player-not-found", new String[]{"%player%", args[1]});
            return true;
         }
      }
   }

   private boolean accept(Player target) {
      DuelModule.DuelRequest duelmodule$duelrequest = this.requests.remove(target.getUniqueId());
      if (duelmodule$duelrequest != null && !duelmodule$duelrequest.expired()) {
         Player player = Bukkit.getPlayer(duelmodule$duelrequest.challenger());
         if (player != null && player.isOnline()) {
            if (!this.activeDuels.containsKey(target.getUniqueId()) && !this.activeDuels.containsKey(player.getUniqueId())) {
               Material material = this.firstBannedMaterial(player);
               if (material != null) {
                  this.send(target, "challenger-banned-item", new String[]{"%player%", player.getName(), "%material%", this.prettyMaterial(material)});
                  this.send(player, "banned-item", new String[]{"%material%", this.prettyMaterial(material)});
                  return true;
               } else {
                  Material material1 = this.firstBannedMaterial(target);
                  if (material1 != null) {
                     this.send(target, "banned-item", new String[]{"%material%", this.prettyMaterial(material1)});
                     this.send(player, "target-banned-item", new String[]{"%player%", target.getName(), "%material%", this.prettyMaterial(material1)});
                     return true;
                  } else {
                     this.removeRequestsInvolving(player.getUniqueId());
                     this.removeRequestsInvolving(target.getUniqueId());
                     DuelModule.ActiveDuel duelmodule$activeduel = new DuelModule.ActiveDuel(player.getUniqueId(), target.getUniqueId());
                     this.activeDuels.put(player.getUniqueId(), duelmodule$activeduel);
                     this.activeDuels.put(target.getUniqueId(), duelmodule$activeduel);
                     this.teleportToArena(player, target);
                     this.send(player, "accepted", new String[]{"%player%", target.getName()});
                     this.send(target, "accepted", new String[]{"%player%", player.getName()});
                     this.startCountdown(duelmodule$activeduel);
                     return true;
                  }
               }
            } else {
               this.send(target, "already-dueling", new String[0]);
               return true;
            }
         } else {
            this.send(target, "challenger-offline", new String[0]);
            return true;
         }
      } else {
         this.send(target, "no-request", new String[0]);
         return true;
      }
   }

   private boolean deny(Player target) {
      DuelModule.DuelRequest duelmodule$duelrequest = this.requests.remove(target.getUniqueId());
      if (duelmodule$duelrequest != null && !duelmodule$duelrequest.expired()) {
         Player player = Bukkit.getPlayer(duelmodule$duelrequest.challenger());
         this.send(target, "request-denied", new String[0]);
         if (player != null) {
            this.send(player, "request-denied-by", new String[]{"%player%", target.getName()});
         }

         return true;
      } else {
         this.send(target, "no-request", new String[0]);
         return true;
      }
   }

   private boolean showElo(Player sender, String[] args) {
      Player player = sender;
      if (args.length > 2) {
         this.send(sender, "elo-usage", new String[0]);
         return true;
      } else {
         if (args.length == 2) {
            player = Bukkit.getPlayerExact(args[1]);
            if (player == null) {
               this.send(sender, "player-not-found", new String[]{"%player%", args[1]});
               return true;
            }
         }

         try {
            DuelModule.EloEntry duelmodule$eloentry = this.elo.get(player.getUniqueId(), player.getName());
            this.send(
               sender,
               "elo",
               new String[]{
                  "%player%",
                  duelmodule$eloentry.name(),
                  "%elo%",
                  String.valueOf(duelmodule$eloentry.rating()),
                  "%rank%",
                  this.rankFor(duelmodule$eloentry.rating()),
                  "%wins%",
                  String.valueOf(duelmodule$eloentry.wins()),
                  "%losses%",
                  String.valueOf(duelmodule$eloentry.losses())
               }
            );
         } catch (SQLException sqlexception) {
            this.databaseFailure(sender, sqlexception);
         }

         return true;
      }
   }

   private boolean showTop(Player sender) {
      int i = Math.max(1, Math.min(100, this.config.getInt("elo.top-limit", 10)));

      try {
         List<DuelModule.EloEntry> list = this.elo.top(i);
         this.send(sender, "top-header", new String[0]);
         if (list.isEmpty()) {
            this.send(sender, "top-empty", new String[0]);
            return true;
         }

         for (int j = 0; j < list.size(); j++) {
            DuelModule.EloEntry duelmodule$eloentry = list.get(j);
            this.send(
               sender,
               "top-line",
               new String[]{
                  "%position%",
                  String.valueOf(j + 1),
                  "%player%",
                  duelmodule$eloentry.name(),
                  "%elo%",
                  String.valueOf(duelmodule$eloentry.rating()),
                  "%rank%",
                  this.rankFor(duelmodule$eloentry.rating()),
                  "%wins%",
                  String.valueOf(duelmodule$eloentry.wins()),
                  "%losses%",
                  String.valueOf(duelmodule$eloentry.losses())
               }
            );
         }
      } catch (SQLException sqlexception) {
         this.databaseFailure(sender, sqlexception);
      }

      return true;
   }

   private boolean leave(Player player) {
      if (this.spectators.containsKey(player.getUniqueId())) {
         this.spectators.remove(player.getUniqueId());
         player.setGameMode(GameMode.SURVIVAL);
         this.send(player, "spectate-ended", new String[0]);
         return true;
      } else {
         DuelModule.ActiveDuel duelmodule$activeduel = this.activeDuels.get(player.getUniqueId());
         if (duelmodule$activeduel != null) {
            Player playerx = Bukkit.getPlayer(duelmodule$activeduel.opponent(player.getUniqueId()));
            if (playerx != null) {
               this.finishDuel(duelmodule$activeduel, playerx, player, "forfeit");
            } else {
               this.cancelDuel(duelmodule$activeduel);
            }

            return true;
         } else if (this.queue.remove(player.getUniqueId()) != null) {
            this.send(player, "queue-left", new String[0]);
            return true;
         } else {
            if (this.removeRequestsInvolving(player.getUniqueId())) {
               this.send(player, "request-cancelled", new String[0]);
            } else {
               this.send(player, "not-dueling", new String[0]);
            }

            return true;
         }
      }
   }

   private void startCountdown(final DuelModule.ActiveDuel duel) {
      final int i = Math.max(0, this.config.getInt("countdown-seconds", 15));
      if (i == 0) {
         this.beginFight(duel);
      } else {
         BukkitRunnable bukkitrunnable = new BukkitRunnable() {
            private int remaining = i;

            public void run() {
               if (!DuelModule.this.activeDuels.containsValue(duel)) {
                  this.cancel();
               } else {
                  Player player = Bukkit.getPlayer(duel.first);
                  Player player1 = Bukkit.getPlayer(duel.second);
                  if (player == null || player1 == null) {
                     this.cancel();
                     DuelModule.this.cancelDuel(duel);
                  } else if (this.remaining <= 0) {
                     this.cancel();
                     DuelModule.this.beginFight(duel);
                  } else {
                     String s = DuelModule.this.config.getString("countdown-actionbar", "&#FFB800Duel starts in &f%seconds%&#FFB800...");
                     String s1 = s.replace("%seconds%", String.valueOf(this.remaining));
                     player.sendActionBar(Text.c(s1));
                     player1.sendActionBar(Text.c(s1));
                     this.remaining--;
                  }
               }
            }
         };
         duel.countdownTask = bukkitrunnable.runTaskTimer(this.plugin, 0L, 20L);
      }
   }

   private void beginFight(DuelModule.ActiveDuel duel) {
      if (this.activeDuels.containsValue(duel)) {
         duel.countdownTask = null;
         duel.started = true;
         String s = this.config.getString("fight-actionbar", "&#9FFF00&lFIGHT!");
         Player player = Bukkit.getPlayer(duel.first);
         Player player1 = Bukkit.getPlayer(duel.second);
         if (player != null) {
            player.sendActionBar(Text.c(s));
            this.send(player, "fight-started", new String[0]);
         }

         if (player1 != null) {
            player1.sendActionBar(Text.c(s));
            this.send(player1, "fight-started", new String[0]);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Player player) {
         Player player1 = this.attackingPlayer(event.getDamager());
         if (player1 != null) {
            DuelModule.ActiveDuel duelmodule$activeduel = this.activeDuels.get(player.getUniqueId());
            DuelModule.ActiveDuel duelmodule$activeduel1 = this.activeDuels.get(player1.getUniqueId());
            if (duelmodule$activeduel != null || duelmodule$activeduel1 != null) {
               if (duelmodule$activeduel == null || duelmodule$activeduel != duelmodule$activeduel1 || !duelmodule$activeduel.started) {
                  event.setCancelled(true);
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      DuelModule.ActiveDuel duelmodule$activeduel = this.activeDuels.get(player.getUniqueId());
      if (duelmodule$activeduel != null) {
         if (this.config.getBoolean("cancel-drops", true)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
         }

         Player player1 = Bukkit.getPlayer(duelmodule$activeduel.opponent(player.getUniqueId()));
         if (player1 == null) {
            this.cancelDuel(duelmodule$activeduel);
         } else {
            this.finishDuel(duelmodule$activeduel, player1, player, "death");
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      this.spectators.remove(player.getUniqueId());
      this.queue.remove(player.getUniqueId());
      DuelModule.ActiveDuel duelmodule$activeduel = this.activeDuels.get(player.getUniqueId());
      if (duelmodule$activeduel != null) {
         Player player1 = Bukkit.getPlayer(duelmodule$activeduel.opponent(player.getUniqueId()));
         if (player1 != null) {
            this.finishDuel(duelmodule$activeduel, player1, player, "forfeit");
         } else {
            this.cancelDuel(duelmodule$activeduel);
         }
      }

      this.removeRequestsInvolving(player.getUniqueId());
   }

   private void finishDuel(DuelModule.ActiveDuel duel, Player winner, Player loser, String reason) {
      this.clearSpectators(duel);
      this.returnPlayers(duel);
      this.cancelDuel(duel);
      this.runMatchEndCommands(winner, loser);

      try {
         DuelModule.EloChange duelmodule$elochange = this.elo.recordResult(winner.getUniqueId(), winner.getName(), loser.getUniqueId(), loser.getName());
         this.send(
            winner,
            "won",
            new String[]{
               "%player%",
               loser.getName(),
               "%change%",
               String.valueOf(duelmodule$elochange.delta()),
               "%elo%",
               String.valueOf(duelmodule$elochange.winnerRating())
            }
         );
         this.send(
            loser,
            "lost",
            new String[]{
               "%player%",
               winner.getName(),
               "%change%",
               String.valueOf(duelmodule$elochange.delta()),
               "%elo%",
               String.valueOf(duelmodule$elochange.loserRating())
            }
         );
         String s = this.raw(
            "broadcast",
            new String[]{
               "%winner%", winner.getName(), "%loser%", loser.getName(), "%reason%", reason, "%change%", String.valueOf(duelmodule$elochange.delta())
            }
         );
         if (s != null && !s.isBlank()) {
            Bukkit.broadcast(Text.c(s));
         }
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().severe("[duel] Could not update Elo: " + sqlexception.getMessage());
         this.send(winner, "result-database-error", new String[0]);
         this.send(loser, "result-database-error", new String[0]);
      }
   }

   private void cancelDuel(DuelModule.ActiveDuel duel) {
      duel.cancelCountdown();
      this.activeDuels.remove(duel.first, duel);
      this.activeDuels.remove(duel.second, duel);
   }

   private Player attackingPlayer(Entity damager) {
      if (damager instanceof Player) {
         return (Player)damager;
      } else {
         if (damager instanceof Projectile projectile) {
            ProjectileSource projectilesource = projectile.getShooter();
            if (projectilesource instanceof Player) {
               return (Player)projectilesource;
            }
         }

         return null;
      }
   }

   private Material firstBannedMaterial(Player player) {
      PlayerInventory playerinventory = player.getInventory();

      for (ItemStack itemstack : playerinventory.getContents()) {
         if (itemstack != null && this.bannedMaterials.contains(itemstack.getType())) {
            return itemstack.getType();
         }
      }

      return null;
   }

   private String prettyMaterial(Material material) {
      String[] astring = material.name().toLowerCase(Locale.ROOT).split("_");
      StringBuilder stringbuilder = new StringBuilder();

      for (String s : astring) {
         if (!stringbuilder.isEmpty()) {
            stringbuilder.append(' ');
         }

         stringbuilder.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
      }

      return stringbuilder.toString();
   }

   private boolean hasPendingRequest(UUID playerId) {
      if (this.requests.containsKey(playerId)) {
         return true;
      } else {
         for (DuelModule.DuelRequest duelmodule$duelrequest : this.requests.values()) {
            if (duelmodule$duelrequest.challenger().equals(playerId)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean removeRequestsInvolving(UUID playerId) {
      boolean flag = this.requests.remove(playerId) != null;
      return this.requests.values().removeIf(request -> request.challenger().equals(playerId)) || flag;
   }

   private void pruneExpiredRequests() {
      this.requests.values().removeIf(DuelModule.DuelRequest::expired);
   }

   private void databaseFailure(CommandSender sender, SQLException ex) {
      this.plugin.getLogger().severe("[duel] Elo database error: " + ex.getMessage());
      this.send(sender, "database-error", new String[0]);
   }

   private void saveConfigFile() {
      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (Exception exception) {
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission("sharded.duel.use")) {
         return List.of();
      } else if (args.length == 1) {
         return TabCompleteHelper.filter(args[0], SUBCOMMANDS);
      } else if (args.length == 2 && args[0].equalsIgnoreCase("arena")) {
         return TabCompleteHelper.filter(args[1], List.of("create", "setspawn1", "setspawn2", "delete", "list", "enable", "disable"));
      } else if (args.length == 2
         && (
            args[0].equalsIgnoreCase("challenge")
               || args[0].equalsIgnoreCase("elo")
               || args[0].equalsIgnoreCase("stats")
               || args[0].equalsIgnoreCase("spectate")
         )) {
         List<String> list = new ArrayList<>();

         for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(sender instanceof Player player1) || !player.getUniqueId().equals(player1.getUniqueId())) {
               list.add(player.getName());
            }
         }

         list.sort(String.CASE_INSENSITIVE_ORDER);
         return TabCompleteHelper.filter(args[1], list);
      } else {
         if (args.length == 3 && args[0].equalsIgnoreCase("arena")) {
            ConfigurationSection configurationsection = this.config.getConfigurationSection("arenas");
            if (configurationsection != null) {
               return TabCompleteHelper.filter(args[2], new ArrayList<>(configurationsection.getKeys(false)));
            }
         }

         return List.of();
      }
   }

   private static final class ActiveDuel {
      private final UUID first;
      private final UUID second;
      private boolean started;
      private BukkitTask countdownTask;
      private Location returnFirst;
      private Location returnSecond;
      private String arenaName;

      private ActiveDuel(UUID first, UUID second) {
         this.first = first;
         this.second = second;
      }

      private UUID opponent(UUID player) {
         return this.first.equals(player) ? this.second : this.first;
      }

      private void cancelCountdown() {
         if (this.countdownTask != null) {
            this.countdownTask.cancel();
            this.countdownTask = null;
         }
      }
   }

   private static record ArenaSpawns(String name, Location pos1, Location pos2) {
   }

   public static record DuelEloEntry(UUID uuid, String name, int elo, String rank) {
   }

   static final class RequestHolder implements InventoryHolder {
      private Inventory inventory;
      private UUID subject;
      private boolean confirm;

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static record DuelRequest(UUID challenger, UUID target, long expiresAt) {
      private boolean expired() {
         return System.currentTimeMillis() >= this.expiresAt;
      }
   }

   public static record DuelWinEntry(UUID uuid, String name, int wins) {
   }

   private static record EloChange(int delta, int winnerRating, int loserRating) {
   }

   private static record EloEntry(UUID uuid, String name, int rating, int wins, int losses) {
   }

   private final class EloRepository implements AutoCloseable {
      private final Connection connection;
      private final int startRating;
      private final int kFactor;

      private EloRepository(File moduleFolder, int startRating, int kFactor) throws SQLException {
         if (!moduleFolder.exists() && !moduleFolder.mkdirs()) {
            throw new SQLException("Could not create module folder " + moduleFolder);
         } else {
            try {
               Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException classnotfoundexception) {
            }

            this.connection = DriverManager.getConnection("jdbc:sqlite:" + new File(moduleFolder, "duel-elo.db").getAbsolutePath());
            this.startRating = startRating;
            this.kFactor = kFactor;

            try (Statement statement = this.connection.createStatement()) {
               statement.executeUpdate("CREATE TABLE IF NOT EXISTS elo(uuid TEXT PRIMARY KEY, name TEXT, elo INT, wins INT, losses INT)");
            }
         }
      }

      private synchronized DuelModule.EloEntry get(UUID uuid, String name) throws SQLException {
         this.ensurePlayer(uuid, name);
         return this.find(uuid);
      }

      private synchronized List<DuelModule.EloEntry> top(int limit) throws SQLException {
         List<DuelModule.EloEntry> list = new ArrayList<>();

         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT uuid, name, elo, wins, losses FROM elo ORDER BY elo DESC, wins DESC, name COLLATE NOCASE ASC LIMIT ?")) {
            preparedstatement.setInt(1, limit);

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               while (resultset.next()) {
                  list.add(this.read(resultset));
               }
            }
         }

         return list;
      }

      private synchronized List<DuelModule.EloEntry> topByWins(int limit) throws SQLException {
         List<DuelModule.EloEntry> list = new ArrayList<>();

         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT uuid, name, elo, wins, losses FROM elo WHERE wins > 0 ORDER BY wins DESC, elo DESC, name COLLATE NOCASE ASC LIMIT ?")) {
            preparedstatement.setInt(1, limit);

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               while (resultset.next()) {
                  list.add(this.read(resultset));
               }
            }
         }

         return list;
      }

      private synchronized DuelModule.EloEntry findOrNull(UUID uuid) throws SQLException {
         DuelModule.EloEntry duelmodule$eloentry;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT uuid, name, elo, wins, losses FROM elo WHERE uuid = ?")) {
            preparedstatement.setString(1, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               if (!resultset.next()) {
                  return null;
               }

               duelmodule$eloentry = this.read(resultset);
            }
         }

         return duelmodule$eloentry;
      }

      private synchronized DuelModule.EloChange recordResult(UUID winnerId, String winnerName, UUID loserId, String loserName) throws SQLException {
         boolean flag = this.connection.getAutoCommit();
         this.connection.setAutoCommit(false);

         DuelModule.EloChange duelmodule$elochange;
         try {
            this.ensurePlayer(winnerId, winnerName);
            this.ensurePlayer(loserId, loserName);
            DuelModule.EloEntry duelmodule$eloentry = this.find(winnerId);
            DuelModule.EloEntry duelmodule$eloentry1 = this.find(loserId);
            double d0 = 1.0 / (1.0 + Math.pow(10.0, (double)(duelmodule$eloentry1.rating() - duelmodule$eloentry.rating()) / 400.0));
            int i = (int)Math.round(32.0 * (1.0 - d0));
            int j = Math.max(5, Math.min(50, i == 0 ? 5 : i));
            if (Math.abs(duelmodule$eloentry.rating() - duelmodule$eloentry1.rating()) < 50) {
               j = 25;
            }

            String s = winnerId + ":" + loserId;
            int k = DuelModule.this.recentPairWins.getOrDefault(s, 0);
            double d1 = k <= 0 ? 1.0 : (k == 1 ? 0.5 : (k == 2 ? 0.25 : 0.0));
            DuelModule.this.recentPairWins.put(s, k + 1);
            int l = DuelModule.this.killstreaks.getOrDefault(winnerId, 0) + 1;
            DuelModule.this.killstreaks.put(winnerId, l);
            DuelModule.this.killstreaks.put(loserId, 0);
            double d2 = l >= 20 ? 1.3 : (l >= 10 ? 1.2 : (l >= 5 ? 1.1 : 1.0));
            j = (int)Math.round((double)j * d1 * d2);
            int i1 = duelmodule$eloentry1.wins() + duelmodule$eloentry1.losses();
            int j1 = j;
            if (i1 < 5) {
               j1 = Math.max(1, j / 2);
            }

            int k1 = duelmodule$eloentry.rating() + j;
            int l1 = Math.max(0, duelmodule$eloentry1.rating() - j1);
            this.update(winnerId, winnerName, k1, true);
            this.update(loserId, loserName, l1, false);
            this.connection.commit();
            duelmodule$elochange = new DuelModule.EloChange(j, k1, l1);
         } catch (RuntimeException | SQLException sqlexception) {
            this.connection.rollback();
            throw sqlexception;
         } finally {
            this.connection.setAutoCommit(flag);
         }

         return duelmodule$elochange;
      }

      private void ensurePlayer(UUID uuid, String name) throws SQLException {
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("INSERT OR IGNORE INTO elo(uuid, name, elo, wins, losses) VALUES (?, ?, ?, 0, 0)")) {
            preparedstatement.setString(1, uuid.toString());
            preparedstatement.setString(2, name);
            preparedstatement.setInt(3, this.startRating);
            preparedstatement.executeUpdate();
         }

         try (PreparedStatement preparedstatement1 = this.connection.prepareStatement("UPDATE elo SET name = ? WHERE uuid = ?")) {
            preparedstatement1.setString(1, name);
            preparedstatement1.setString(2, uuid.toString());
            preparedstatement1.executeUpdate();
         }
      }

      private DuelModule.EloEntry find(UUID uuid) throws SQLException {
         DuelModule.EloEntry duelmodule$eloentry;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT uuid, name, elo, wins, losses FROM elo WHERE uuid = ?")) {
            preparedstatement.setString(1, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               if (!resultset.next()) {
                  throw new SQLException("Missing Elo row for " + uuid);
               }

               duelmodule$eloentry = this.read(resultset);
            }
         }

         return duelmodule$eloentry;
      }

      private DuelModule.EloEntry read(ResultSet result) throws SQLException {
         return new DuelModule.EloEntry(
            UUID.fromString(result.getString("uuid")), result.getString("name"), result.getInt("elo"), result.getInt("wins"), result.getInt("losses")
         );
      }

      private void update(UUID uuid, String name, int rating, boolean winner) throws SQLException {
         String s = winner
            ? "UPDATE elo SET name = ?, elo = ?, wins = wins + 1 WHERE uuid = ?"
            : "UPDATE elo SET name = ?, elo = ?, losses = losses + 1 WHERE uuid = ?";

         try (PreparedStatement preparedstatement = this.connection.prepareStatement(s)) {
            preparedstatement.setString(1, name);
            preparedstatement.setInt(2, rating);
            preparedstatement.setString(3, uuid.toString());
            preparedstatement.executeUpdate();
         }
      }

      @Override
      public synchronized void close() {
         try {
            this.connection.close();
         } catch (SQLException sqlexception) {
         }
      }
   }
}
