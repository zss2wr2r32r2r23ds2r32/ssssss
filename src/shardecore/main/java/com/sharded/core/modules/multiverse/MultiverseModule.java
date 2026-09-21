package com.sharded.core.modules.multiverse;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.spawnselect.SpawnSelectModule;
import com.sharded.core.util.TabCompleteHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.World.Environment;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class MultiverseModule extends Module implements CommandExecutor, TabCompleter {
   private static final List<String> SUBS = List.of(
      "import", "tp", "teleport", "setspawn", "delete", "remove", "list", "gamerule", "worldborder", "border", "world", "load", "unload", "info"
   );
   private final Map<UUID, Map<String, MultiverseModule.PlayerSnapshot>> snapshots = new HashMap<>();
   private YamlConfiguration worldsConfig;
   private File worldsFile;
   private BukkitTask scanTask;

   public MultiverseModule(ShardedCore plugin) {
      super(plugin, "multiverse");
   }

   @Override
   protected void onEnable() {
      this.worldsFile = new File(this.moduleFolder(), "worlds.yml");
      this.worldsConfig = YamlConfiguration.loadConfiguration(this.worldsFile);
      this.registerCommand("mv", this);
      this.registerCommand("multiverse", this);
      this.registerListener(this);
      Bukkit.getScheduler().runTaskLater(this.plugin, this::importDefaultsAndScan, 40L);
      long i = Math.max(100L, this.config.getLong("rescan-seconds", 30L) * 20L);
      this.scanTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::scanNewWorldFolders, i, i);
      this.plugin.getLogger().info("[multiverse] Enabled. Use /mv import|tp|setspawn|delete|gamerule|worldborder");
   }

   @Override
   protected void onDisable() {
      if (this.scanTask != null) {
         this.scanTask.cancel();
         this.scanTask = null;
      }

      this.saveWorlds();
      this.snapshots.clear();
   }

   private void importDefaultsAndScan() {
      List<String> list = this.config.getStringList("auto-import");
      if (list.isEmpty()) {
         list = List.of("world", "world_nether", "world_end", "spawn", "duels");
      }

      for (String s : list) {
         if (Bukkit.getWorld(s) == null && this.folderLooksLikeWorld(s)) {
            this.loadWorld(s, true);
         }
      }

      this.scanNewWorldFolders();
      this.applyStoredBordersAndSpawns();
   }

   private void scanNewWorldFolders() {
      if (this.config.getBoolean("auto-import-all", true)) {
         File file1 = Bukkit.getWorldContainer();
         File[] afile = file1.listFiles();
         if (afile != null) {
            for (File file2 : afile) {
               if (file2.isDirectory()) {
                  String s = file2.getName();
                  if (!s.equals("plugins")
                     && !s.equals("logs")
                     && !s.equals("cache")
                     && !s.equals("crash-reports")
                     && !s.startsWith(".")
                     && new File(file2, "level.dat").exists()
                     && Bukkit.getWorld(s) == null) {
                     this.loadWorld(s, true);
                  }
               }
            }
         }
      }
   }

   private boolean folderLooksLikeWorld(String name) {
      return new File(new File(Bukkit.getWorldContainer(), name), "level.dat").exists();
   }

   private void applyStoredBordersAndSpawns() {
      ConfigurationSection configurationsection = this.worldsConfig.getConfigurationSection("worlds");
      if (configurationsection != null) {
         for (String s : configurationsection.getKeys(false)) {
            World world = Bukkit.getWorld(s);
            if (world != null) {
               if (this.worldsConfig.contains("worlds." + s + ".border-size")) {
                  world.getWorldBorder().setSize(this.worldsConfig.getDouble("worlds." + s + ".border-size"));
               }

               if (this.worldsConfig.contains("worlds." + s + ".spawn.x")) {
                  Location stored = this.readSpawn(s);
                  if (stored != null && stored.getWorld() != null && !this.isMainHub(stored)) {
                     world.setSpawnLocation(stored);
                  }
               }

               ConfigurationSection configurationsection1 = this.worldsConfig.getConfigurationSection("worlds." + s + ".gamerules");
               if (configurationsection1 != null) {
                  for (String s1 : configurationsection1.getKeys(false)) {
                     GameRule<?> gamerule = GameRule.getByName(s1);
                     if (gamerule != null) {
                        try {
                           if (gamerule.getType() == Boolean.class) {
                              @SuppressWarnings("unchecked")
                              GameRule<Boolean> rule = (GameRule<Boolean>) gamerule;
                              world.setGameRule(rule, configurationsection1.getBoolean(s1));
                           } else if (gamerule.getType() == Integer.class) {
                              @SuppressWarnings("unchecked")
                              GameRule<Integer> rule = (GameRule<Integer>) gamerule;
                              world.setGameRule(rule, configurationsection1.getInt(s1));
                           }
                        } catch (Throwable throwable) {
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!sender.isOp() && !sender.hasPermission("sharded.multiverse.admin")) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else if (args.length == 0) {
         this.send(sender, "usage", new String[0]);
         return true;
      } else if (args[0].equalsIgnoreCase("world") && args.length >= 2 && args[1].equalsIgnoreCase("border")) {
         String[] astring = new String[args.length - 1];
         astring[0] = "worldborder";
         System.arraycopy(args, 2, astring, 1, args.length - 2);
         return this.cmdBorder(sender, astring);
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);

         return switch (s) {
            case "import", "load" -> this.cmdImport(sender, args);
            case "tp", "teleport" -> this.cmdTp(sender, args);
            case "setspawn" -> this.cmdSetSpawn(sender, args);
            case "delete", "remove" -> this.cmdDelete(sender, args);
            case "list" -> this.cmdList(sender);
            case "gamerule" -> this.cmdGamerule(sender, args);
            case "worldborder", "border" -> this.cmdBorder(sender, args);
            case "unload" -> this.cmdUnload(sender, args);
            case "info" -> this.cmdInfo(sender, args);
            default -> {
               this.send(sender, "usage", new String[0]);
               yield true;
            }
         };
      }
   }

   private boolean cmdImport(CommandSender sender, String[] args) {
      if (args.length < 2) {
         this.send(sender, "usage-import", new String[0]);
         return true;
      } else {
         String s = args[1];
         if (Bukkit.getWorld(s) != null) {
            this.send(sender, "already-loaded", new String[]{"%world%", s});
            return true;
         } else if (!this.folderLooksLikeWorld(s)) {
            this.send(sender, "missing-world", new String[]{"%world%", s});
            return true;
         } else {
            World world = this.loadWorld(s, true);
            if (world == null) {
               this.send(sender, "import-failed", new String[]{"%world%", s});
               return true;
            } else {
               this.send(sender, "imported", new String[]{"%world%", world.getName()});
               return true;
            }
         }
      }
   }

   private World loadWorld(String name, boolean mark) {
      try {
         WorldCreator worldcreator = new WorldCreator(name);
         String s = name.toLowerCase(Locale.ROOT);
         if (s.endsWith("_nether")) {
            worldcreator.environment(Environment.NETHER);
         } else if (!s.endsWith("_the_end") && !s.endsWith("_end")) {
            worldcreator.environment(Environment.NORMAL);
         } else {
            worldcreator.environment(Environment.THE_END);
         }

         worldcreator.type(WorldType.NORMAL);
         World world = worldcreator.createWorld();
         if (world != null && mark) {
            this.worldsConfig.set("worlds." + name + ".imported", true);
            this.saveWorlds();
         }

         if (world != null) {
            this.plugin.getLogger().info("[multiverse] Loaded world " + world.getName());
         }

         return world;
      } catch (Throwable throwable) {
         this.plugin.getLogger().warning("[multiverse] Failed to load world " + name + ": " + throwable.getMessage());
         return null;
      }
   }

   private boolean cmdTp(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         this.send(sender, "players-only", new String[0]);
         return true;
      } else if (args.length < 2) {
         this.send(player, "usage-tp", new String[0]);
         return true;
      } else {
         String s = args[1];
         World world = Bukkit.getWorld(s);
         if (world == null && this.folderLooksLikeWorld(s)) {
            world = this.loadWorld(s, true);
         }

         if (world == null) {
            for (World world1 : Bukkit.getWorlds()) {
               if (world1.getName().equalsIgnoreCase(s)) {
                  world = world1;
                  break;
               }
            }
         }

         if (world == null && this.folderLooksLikeWorld(s)) {
            world = this.loadWorld(s, true);
         }

         if (world == null) {
            this.send(player, "missing-world", new String[]{"%world%", s});
            return true;
         } else {
            Location location = this.worldTeleportLocation(world);
            player.teleport(location);
            this.send(player, "teleported", new String[]{"%world%", world.getName()});
            return true;
         }
      }
   }

   private boolean cmdSetSpawn(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         this.send(sender, "players-only", new String[0]);
         return true;
      } else {
         World world;
         Location location;
         if (args.length >= 2) {
            world = Bukkit.getWorld(args[1]);
            if (world == null) {
               for (World world1 : Bukkit.getWorlds()) {
                  if (world1.getName().equalsIgnoreCase(args[1])) {
                     world = world1;
                     break;
                  }
               }
            }

            if (world == null) {
               this.send(player, "missing-world", new String[]{"%world%", args[1]});
               return true;
            }

            if (!player.getWorld().equals(world)) {
               this.send(player, "setspawn-wrong-world", new String[]{"%world%", world.getName()});
               return true;
            }

            location = player.getLocation();
         } else {
            world = player.getWorld();
            location = player.getLocation();
         }

         if (world == null) {
            return true;
         } else {
            world.setSpawnLocation(location);
            String s = "worlds." + world.getName() + ".spawn";
            this.worldsConfig.set(s + ".x", location.getX());
            this.worldsConfig.set(s + ".y", location.getY());
            this.worldsConfig.set(s + ".z", location.getZ());
            this.worldsConfig.set(s + ".yaw", location.getYaw());
            this.worldsConfig.set(s + ".pitch", location.getPitch());
            this.worldsConfig.set("worlds." + world.getName() + ".imported", true);
            this.saveWorlds();
            this.send(player, "spawn-set", new String[]{"%world%", world.getName()});
            return true;
         }
      }
   }

   private boolean cmdDelete(CommandSender sender, String[] args) {
      if (args.length < 2) {
         this.send(sender, "usage-delete", new String[0]);
         return true;
      } else {
         String s = args[1];
         World world = Bukkit.getWorlds().isEmpty() ? null : (World)Bukkit.getWorlds().getFirst();
         if (world != null && s.equalsIgnoreCase(world.getName()) && !this.config.getBoolean("allow-delete-main", false)) {
            this.send(sender, "delete-main-blocked", new String[0]);
            return true;
         } else {
            World world1 = Bukkit.getWorld(s);
            if (world1 != null) {
               Location location = world == null ? null : world.getSpawnLocation();

               for (Player player : new ArrayList<Player>(world1.getPlayers())) {
                  if (location != null) {
                     player.teleport(location);
                  }
               }

               if (!Bukkit.unloadWorld(world1, false)) {
                  this.send(sender, "unload-failed", new String[]{"%world%", s});
                  return true;
               }
            }

            File file1 = new File(Bukkit.getWorldContainer(), s);
            boolean flag = this.deleteRecursive(file1);
            this.worldsConfig.set("worlds." + s, null);
            this.saveWorlds();
            this.send(sender, flag ? "deleted" : "delete-partial", new String[]{"%world%", s});
            return true;
         }
      }
   }

   private boolean cmdUnload(CommandSender sender, String[] args) {
      if (args.length < 2) {
         this.send(sender, "usage-delete", new String[0]);
         return true;
      } else {
         World world = Bukkit.getWorld(args[1]);
         if (world == null) {
            this.send(sender, "missing-world", new String[]{"%world%", args[1]});
            return true;
         } else {
            Location location = ((World)Bukkit.getWorlds().getFirst()).getSpawnLocation();

            for (Player player : new ArrayList<Player>(world.getPlayers())) {
               player.teleport(location);
            }

            boolean flag = Bukkit.unloadWorld(world, true);
            this.send(sender, flag ? "unloaded" : "unload-failed", new String[]{"%world%", args[1]});
            return true;
         }
      }
   }

   private boolean cmdList(CommandSender sender) {
      this.send(sender, "list-header", new String[0]);

      for (World world : Bukkit.getWorlds()) {
         this.send(
            sender,
            "list-line",
            new String[]{
               "%world%",
               world.getName(),
               "%players%",
               String.valueOf(world.getPlayers().size()),
               "%env%",
               world.getEnvironment().name().toLowerCase(Locale.ROOT)
            }
         );
      }

      return true;
   }

   private boolean cmdGamerule(CommandSender sender, String[] args) {
      if (args.length < 4) {
         this.send(sender, "usage-gamerule", new String[0]);
         return true;
      } else {
         World world = Bukkit.getWorld(args[1]);
         if (world == null) {
            this.send(sender, "missing-world", new String[]{"%world%", args[1]});
            return true;
         } else {
            String s;
            String s1;
            if (args[2].equalsIgnoreCase("set")) {
               if (args.length < 5) {
                  this.send(sender, "usage-gamerule", new String[0]);
                  return true;
               }

               s = args[3];
               s1 = args[4];
            } else {
               s = args[2];
               s1 = args[3];
            }

            GameRule<?> gamerule = GameRule.getByName(s);
            if (gamerule == null) {
               this.send(sender, "unknown-gamerule", new String[]{"%rule%", s});
               return true;
            } else {
               try {
                  if (gamerule.getType() == Boolean.class) {
                     boolean flag = Boolean.parseBoolean(s1);
                     @SuppressWarnings("unchecked")
                     GameRule<Boolean> rule = (GameRule<Boolean>) gamerule;
                     world.setGameRule(rule, flag);
                     this.worldsConfig.set("worlds." + world.getName() + ".gamerules." + gamerule.getName(), flag);
                  } else {
                     if (gamerule.getType() != Integer.class) {
                        this.send(sender, "unsupported-gamerule", new String[]{"%rule%", s});
                        return true;
                     }

                     int i = Integer.parseInt(s1);
                     @SuppressWarnings("unchecked")
                     GameRule<Integer> rule = (GameRule<Integer>) gamerule;
                     world.setGameRule(rule, i);
                     this.worldsConfig.set("worlds." + world.getName() + ".gamerules." + gamerule.getName(), i);
                  }

                  this.saveWorlds();
                  this.send(sender, "gamerule-set", new String[]{"%world%", world.getName(), "%rule%", gamerule.getName(), "%value%", s1});
               } catch (Exception exception) {
                  this.send(sender, "gamerule-failed", new String[]{"%rule%", s});
               }

               return true;
            }
         }
      }
   }

   private boolean cmdBorder(CommandSender sender, String[] args) {
      if (args.length >= 4 && args[2].equalsIgnoreCase("set")) {
         World world = Bukkit.getWorld(args[1]);
         if (world == null) {
            this.send(sender, "missing-world", new String[]{"%world%", args[1]});
            return true;
         } else {
            double d0;
            try {
               d0 = Double.parseDouble(args[3]);
            } catch (NumberFormatException numberformatexception) {
               this.send(sender, "invalid-number", new String[]{"%value%", args[3]});
               return true;
            }

            world.getWorldBorder().setSize(Math.max(1.0, d0));
            this.worldsConfig.set("worlds." + world.getName() + ".border-size", d0);
            this.saveWorlds();
            this.send(sender, "border-set", new String[]{"%world%", world.getName(), "%size%", String.valueOf(d0)});
            return true;
         }
      } else {
         this.send(sender, "usage-border", new String[0]);
         return true;
      }
   }

   private boolean cmdInfo(CommandSender sender, String[] args) {
      String s1;
      if (args.length >= 2) {
         s1 = args[1];
      } else {
         label18: {
            if (sender instanceof Player player && player.getWorld() != null) {
               s1 = player.getWorld().getName();
               break label18;
            }

            s1 = "world";
         }
      }

      String s = s1;
      World world = Bukkit.getWorld(s);
      if (world == null) {
         this.send(sender, "missing-world", new String[]{"%world%", s});
         return true;
      } else {
         Location location = world.getSpawnLocation();
         this.send(
            sender,
            "info",
            new String[]{
               "%world%",
               world.getName(),
               "%env%",
               world.getEnvironment().name().toLowerCase(Locale.ROOT),
               "%players%",
               String.valueOf(world.getPlayers().size()),
               "%border%",
               String.valueOf((int)world.getWorldBorder().getSize()),
               "%spawn%",
               location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()
            }
         );
         return true;
      }
   }

   /**
    * Teleport destination for {@code /mv tp <world>}: that world's spawn, never spawnselect main hub.
    */
   private Location worldTeleportLocation(World world) {
      Location worldSpawn = world.getSpawnLocation().clone();
      if (worldSpawn.getWorld() == null) {
         worldSpawn.setWorld(world);
      }
      Location stored = this.readSpawn(world.getName());
      if (stored != null && stored.getWorld() != null && stored.getWorld().equals(world) && !this.isMainHub(stored)) {
         return stored;
      }
      if (this.isMainHub(worldSpawn)) {
         Location fallback = this.vanillaWorldSpawn(world);
         if (fallback != null && !this.isMainHub(fallback)) {
            return fallback;
         }
      }
      return worldSpawn;
   }

   private Location vanillaWorldSpawn(World world) {
      Location location = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1.0, 0.5);
      return location.getBlock().isPassable() || !location.clone().subtract(0.0, 1.0, 0.0).getBlock().getType().isAir()
         ? location
         : null;
   }

   private boolean isMainHub(Location location) {
      if (location == null || location.getWorld() == null) {
         return false;
      }
      SpawnSelectModule spawnselect = this.plugin.modules().get(SpawnSelectModule.class);
      if (spawnselect == null || !spawnselect.isEnabled()) {
         return false;
      }
      Location hub = spawnselect.mainSpawn();
      if (hub == null || hub.getWorld() == null) {
         return false;
      }
      return location.getWorld().equals(hub.getWorld())
         && location.getBlockX() == hub.getBlockX()
         && location.getBlockY() == hub.getBlockY()
         && location.getBlockZ() == hub.getBlockZ();
   }

   private Location readSpawn(String worldName) {
      String s = "worlds." + worldName + ".spawn";
      if (!this.worldsConfig.contains(s + ".x")) {
         return null;
      } else {
         World world = Bukkit.getWorld(worldName);
         return world == null
            ? null
            : new Location(
               world,
               this.worldsConfig.getDouble(s + ".x"),
               this.worldsConfig.getDouble(s + ".y"),
               this.worldsConfig.getDouble(s + ".z"),
               (float)this.worldsConfig.getDouble(s + ".yaw", 0.0),
               (float)this.worldsConfig.getDouble(s + ".pitch", 0.0)
            );
      }
   }

   private boolean deleteRecursive(File file) {
      if (file != null && file.exists()) {
         if (file.isDirectory()) {
            File[] afile = file.listFiles();
            if (afile != null) {
               for (File file1 : afile) {
                  this.deleteRecursive(file1);
               }
            }
         }

         return file.delete();
      } else {
         return false;
      }
   }

   private void saveWorlds() {
      try {
         this.worldsConfig.save(this.worldsFile);
      } catch (Exception exception) {
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onMvCommand(PlayerCommandPreprocessEvent event) {
      String raw = event.getMessage();
      if (raw == null || raw.length() < 4) {
         return;
      }
      String body = raw.startsWith("/") ? raw.substring(1) : raw;
      String[] parts = body.split("\\s+");
      if (parts.length < 3) {
         return;
      }
      String cmd = parts[0].toLowerCase(Locale.ROOT);
      if (cmd.contains(":")) {
         cmd = cmd.substring(cmd.indexOf(':') + 1);
      }
      if (!cmd.equals("mv") && !cmd.equals("multiverse") && !cmd.equals("mvtp")) {
         return;
      }
      Player player = event.getPlayer();
      if (!player.isOp() && !player.hasPermission("sharded.multiverse.admin")) {
         return;
      }
      if (cmd.equals("mvtp")) {
         String[] args = new String[parts.length];
         args[0] = "tp";
         System.arraycopy(parts, 1, args, 1, parts.length - 1);
         event.setCancelled(true);
         this.cmdTp(player, args);
         return;
      }
      if (parts[1].equalsIgnoreCase("tp") || parts[1].equalsIgnoreCase("teleport")) {
         event.setCancelled(true);
         this.cmdTp(player, java.util.Arrays.copyOfRange(parts, 1, parts.length));
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onJoin(PlayerJoinEvent event) {
      if (!this.config.getBoolean("cross-world-inventories", true)) {
         this.loadIsolated(event.getPlayer(), event.getPlayer().getWorld().getName());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onWorldChange(PlayerChangedWorldEvent event) {
      if (!this.config.getBoolean("cross-world-inventories", true)
         || !this.config.getBoolean("cross-world-enderchests", true)
         || !this.config.getBoolean("cross-world-stats", true)) {
         Player player = event.getPlayer();
         String s = event.getFrom().getName();
         String s1 = player.getWorld().getName();
         if (!this.config.getBoolean("cross-world-inventories", true) || !s.equalsIgnoreCase(s1)) {
            this.saveIsolated(player, s);
            this.loadIsolated(player, s1);
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      if (!this.config.getBoolean("cross-world-inventories", true)) {
         Player player = event.getPlayer();
         this.saveIsolated(player, player.getWorld().getName());
      }
   }

   private void saveIsolated(Player player, String world) {
      Map<String, MultiverseModule.PlayerSnapshot> map = this.snapshots.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
      ItemStack[] aitemstack = Arrays.stream(player.getInventory().getContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
      ItemStack[] aitemstack1 = Arrays.stream(player.getInventory().getArmorContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
      ItemStack itemstack = player.getInventory().getItemInOffHand();
      ItemStack[] aitemstack2 = Arrays.stream(player.getEnderChest().getContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
      map.put(
         world.toLowerCase(Locale.ROOT),
         new MultiverseModule.PlayerSnapshot(
            aitemstack,
            aitemstack1,
            itemstack == null ? null : itemstack.clone(),
            aitemstack2,
            player.getExp(),
            player.getLevel(),
            player.getFoodLevel(),
            player.getHealth()
         )
      );
   }

   private void loadIsolated(Player player, String world) {
      Map<String, MultiverseModule.PlayerSnapshot> map = this.snapshots.get(player.getUniqueId());
      MultiverseModule.PlayerSnapshot multiversemodule$playersnapshot = map == null ? null : map.get(world.toLowerCase(Locale.ROOT));
      if (multiversemodule$playersnapshot == null) {
         player.getInventory().clear();
         player.getInventory().setArmorContents(null);
         if (!this.config.getBoolean("cross-world-enderchests", true)) {
            player.getEnderChest().clear();
         }
      } else {
         player.getInventory().setContents(multiversemodule$playersnapshot.contents());
         player.getInventory().setArmorContents(multiversemodule$playersnapshot.armor());
         player.getInventory().setItemInOffHand(multiversemodule$playersnapshot.offhand());
         if (!this.config.getBoolean("cross-world-enderchests", true)) {
            player.getEnderChest().setContents(multiversemodule$playersnapshot.ender());
         }

         if (!this.config.getBoolean("cross-world-stats", true)) {
            player.setExp(multiversemodule$playersnapshot.exp());
            player.setLevel(multiversemodule$playersnapshot.level());
            player.setFoodLevel(multiversemodule$playersnapshot.food());

            try {
               double d0 = player.getAttribute(Attribute.MAX_HEALTH).getValue();
               player.setHealth(Math.min(multiversemodule$playersnapshot.health(), d0));
            } catch (Throwable throwable) {
            }
         }
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.isOp() && !sender.hasPermission("sharded.multiverse.admin")) {
         return List.of();
      } else if (args.length == 1) {
         return TabCompleteHelper.filter(args[0], SUBS);
      } else {
         if (args.length == 2) {
            String s = args[0].toLowerCase(Locale.ROOT);
            if (s.equals("import") || s.equals("load")) {
               return TabCompleteHelper.filter(args[1], this.folderWorldNames());
            }

            if (s.equals("world")) {
               return TabCompleteHelper.filter(args[1], List.of("border"));
            }

            if (List.of("tp", "teleport", "delete", "remove", "unload", "info", "gamerule", "worldborder", "border", "setspawn").contains(s)) {
               List<String> list1 = new ArrayList<>();

               for (World world1 : Bukkit.getWorlds()) {
                  list1.add(world1.getName());
               }

               list1.addAll(this.folderWorldNames());
               return TabCompleteHelper.filter(args[1], list1.stream().distinct().toList());
            }
         }

         if (args.length == 3) {
            if (args[0].equalsIgnoreCase("gamerule")) {
               return TabCompleteHelper.filter(args[2], this.gameruleNamesWithSet());
            }

            if (args[0].equalsIgnoreCase("worldborder")
               || args[0].equalsIgnoreCase("border")
               || args[0].equalsIgnoreCase("world") && args[1].equalsIgnoreCase("border")) {
               if (!args[0].equalsIgnoreCase("world")) {
                  return TabCompleteHelper.filter(args[2], List.of("set"));
               }

               List<String> list = new ArrayList<>();

               for (World world : Bukkit.getWorlds()) {
                  list.add(world.getName());
               }

               return TabCompleteHelper.filter(args[2], list);
            }
         }

         if (args.length == 4 && args[0].equalsIgnoreCase("gamerule") && args[2].equalsIgnoreCase("set")) {
            return TabCompleteHelper.filter(args[3], this.gameruleNames());
         } else {
            return args.length == 4 && args[0].equalsIgnoreCase("world") && args[1].equalsIgnoreCase("border")
               ? TabCompleteHelper.filter(args[3], List.of("set"))
               : List.of();
         }
      }
   }

   private List<String> gameruleNames() {
      List<String> list = new ArrayList<>();

      for (GameRule<?> gamerule : GameRule.values()) {
         list.add(gamerule.getName());
      }

      return list;
   }

   private List<String> gameruleNamesWithSet() {
      List<String> list = new ArrayList<>();
      list.add("set");
      list.addAll(this.gameruleNames());
      return list;
   }

   private List<String> folderWorldNames() {
      List<String> list = new ArrayList<>();
      File[] afile = Bukkit.getWorldContainer().listFiles();
      if (afile == null) {
         return list;
      } else {
         for (File file1 : afile) {
            if (file1.isDirectory() && new File(file1, "level.dat").exists()) {
               list.add(file1.getName());
            }
         }

         return list;
      }
   }

   private static record PlayerSnapshot(
      ItemStack[] contents, ItemStack[] armor, ItemStack offhand, ItemStack[] ender, float exp, int level, int food, double health
   ) {
   }
}
