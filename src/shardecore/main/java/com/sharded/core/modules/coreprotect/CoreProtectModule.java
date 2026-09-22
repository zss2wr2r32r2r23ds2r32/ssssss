package com.sharded.core.modules.coreprotect;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.CuboidRegion;
import com.sharded.core.util.RegionSetup;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class CoreProtectModule extends Module implements CommandExecutor, TabCompleter {
   private static final List<String> SIDE_KEYS = List.of("side1", "side2", "side3", "side4");
   private static final List<String> REGION_IDS = List.of("spawn", "combat", "pvp", "side1", "side2", "side3", "side4");
   private static final Set<Material> SPAWN_BLOCKED_USE = Set.of(
      Material.ANVIL,
      Material.CHIPPED_ANVIL,
      Material.DAMAGED_ANVIL,
      Material.BEACON,
      Material.OAK_TRAPDOOR,
      Material.SPRUCE_TRAPDOOR,
      Material.BIRCH_TRAPDOOR,
      Material.JUNGLE_TRAPDOOR,
      Material.ACACIA_TRAPDOOR,
      Material.DARK_OAK_TRAPDOOR,
      Material.MANGROVE_TRAPDOOR,
      Material.CHERRY_TRAPDOOR,
      Material.BAMBOO_TRAPDOOR,
      Material.CRIMSON_TRAPDOOR,
      Material.WARPED_TRAPDOOR,
      Material.IRON_TRAPDOOR
   );
   private final RegionSetup setup = new RegionSetup();
   private final Map<String, CuboidRegion> regions = new HashMap<>();
   private Set<String> sideWorlds = Set.of();
   private Set<String> hornBlockWorlds = Set.of("spawn");
   private ArenaService arenaService;
   private PlayerPlacedTracker placedTracker;
   private BukkitTask autoResetTask;

   public CoreProtectModule(ShardedCore plugin) {
      super(plugin, "coreprotect");
   }

   @Override
   protected void onEnable() {
      this.migrateLegacyData();
      this.reloadRegions();
      this.arenaService = new ArenaService(this.plugin, this.moduleFolder());
      this.placedTracker = new PlayerPlacedTracker(this.moduleFolder());
      this.startAutoReset();
   }

   private void migrateLegacyData() {
      File file2 = this.moduleFolder();
      File file3 = new File(this.plugin.getDataFolder(), "modules/protect/config.yml");
      File file4 = new File(this.plugin.getDataFolder(), "modules/arena");
      if (!file2.exists()) {
         file2.mkdirs();
      }

      boolean flag = this.config.getConfigurationSection("regions.spawn") == null
         || CuboidRegion.fromSection(this.config.getConfigurationSection("regions.spawn")) == null;
      if (flag && file3.isFile()) {
         YamlConfiguration yamlconfiguration1 = YamlConfiguration.loadConfiguration(file3);

         for (String s : REGION_IDS) {
            ConfigurationSection configurationsection;
            if ((
                  this.config.getConfigurationSection("regions." + s) == null
                     || CuboidRegion.fromSection(this.config.getConfigurationSection("regions." + s)) == null
               )
               && (configurationsection = yamlconfiguration1.getConfigurationSection("regions." + s)) != null) {
               this.config.set("regions." + s, configurationsection.getValues(false));
            }
         }

         if (yamlconfiguration1.contains("side-max-build-y")) {
            this.config.set("side-max-build-y", yamlconfiguration1.getInt("side-max-build-y"));
         }

         if (yamlconfiguration1.contains("horn-block-worlds")) {
            this.config.set("horn-block-worlds", yamlconfiguration1.getStringList("horn-block-worlds"));
         }

         this.saveConfig();
         this.plugin.getLogger().info("[coreprotect] Imported regions from legacy modules/protect/config.yml");
      }

      File file7 = new File(file4, "snapshots");
      File file8 = new File(file2, "snapshots");
      if (file7.isDirectory() && file7.list() != null && file7.list().length > 0) {
         if (!file8.exists()) {
            file8.mkdirs();
         }

         for (File file5 : file7.listFiles((dir, name) -> name.endsWith(".snap"))) {
            File file6 = new File(file8, file5.getName());
            if (!file6.exists()) {
               try {
                  Files.copy(file5.toPath(), file6.toPath());
               } catch (Exception exception) {
                  this.plugin.getLogger().warning("[coreprotect] Could not copy snapshot " + file5.getName());
               }
            }
         }
      }

      YamlConfiguration yamlconfiguration;
      File file1;
      if ((file1 = new File(file4, "config.yml")).isFile()
         && !this.config.contains("auto-reset.enabled")
         && (yamlconfiguration = YamlConfiguration.loadConfiguration(file1)).contains("auto-reset")) {
         this.config.set("auto-reset", yamlconfiguration.getConfigurationSection("auto-reset").getValues(false));
         this.saveConfig();
      }
   }

   @Override
   protected void onDisable() {
      if (this.autoResetTask != null) {
         this.autoResetTask.cancel();
      }

      if (this.placedTracker != null) {
         this.placedTracker.save();
      }
   }

   private void reloadRegions() {
      this.regions.clear();
      HashSet<String> hashset = new HashSet<>();

      for (String s : REGION_IDS) {
         CuboidRegion cuboidregion = CuboidRegion.fromSection(this.config.getConfigurationSection("regions." + s));
         if (cuboidregion != null) {
            this.regions.put(s, cuboidregion);
            if (SIDE_KEYS.contains(s)) {
               hashset.add(cuboidregion.world().toLowerCase(Locale.ROOT));
            }
         }
      }

      this.sideWorlds = Set.copyOf(hashset);
      List list = this.config.getStringList("horn-block-worlds");
      this.hornBlockWorlds = (Set<String>)(list.isEmpty() ? Set.of("spawn") : new HashSet<>(list));
   }

   public CuboidRegion region(String id) {
      return this.regions.get(id);
   }

   public boolean inSpawn(Location loc) {
      CuboidRegion cuboidregion = this.regions.get("spawn");
      return cuboidregion != null && cuboidregion.contains(loc);
   }

   public boolean inCombat(Location loc) {
      CuboidRegion cuboidregion = this.regions.get("combat");
      return cuboidregion != null && cuboidregion.contains(loc);
   }

   public boolean inPvp(Location loc) {
      CuboidRegion cuboidregion = this.regions.get("pvp");
      return cuboidregion != null && cuboidregion.contains(loc);
   }

   public String sideAt(Location loc) {
      if (loc != null && loc.getWorld() != null) {
         int i = loc.getBlockX();
         int j = loc.getBlockY();
         int k = loc.getBlockZ();

         for (String s : SIDE_KEYS) {
            CuboidRegion cuboidregion = this.regions.get(s);
            if (cuboidregion != null
               && i >= cuboidregion.minX()
               && i <= cuboidregion.maxX()
               && j >= cuboidregion.minY()
               && j <= cuboidregion.maxY()
               && k >= cuboidregion.minZ()
               && k <= cuboidregion.maxZ()) {
               return s;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public boolean inSide(Location loc) {
      return this.sideAt(loc) != null;
   }

   public boolean bypass(Player player) {
      return player.hasPermission("sharded.coreprotect.bypass") || player.hasPermission("sharded.protect.bypass");
   }

   private boolean hornBlocked(Player player) {
      if (this.bypass(player)) {
         return false;
      } else {
         String s = player.getWorld().getName();
         if (this.inSpawn(player.getLocation())) {
            return true;
         } else {
            for (String s1 : this.hornBlockWorlds) {
               if (s1.equalsIgnoreCase(s)) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   private boolean restrictSpawnPvp(Player player, Location loc) {
      if (this.bypass(player)) {
         return false;
      } else {
         return this.inSide(loc) ? false : this.inSpawn(loc) || this.inPvp(loc);
      }
   }

   private boolean restrictSpawnOnly(Player player, Location loc) {
      if (this.bypass(player)) {
         return false;
      } else {
         return this.inSide(loc) ? false : this.inSpawn(loc);
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      return true;
   }

   private boolean handleArenas(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.send(sender, "arenas-usage", new String[0]);
         return true;
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);
         if (s.equals("snapshot") && args.length >= 2) {
            if (!sender.hasPermission("sharded.coreprotect.admin") && !sender.hasPermission("sharded.arena.admin")) {
               this.send(sender, "no-permission", new String[0]);
               return true;
            } else {
               List<String> list = this.resolveArenaIds(args[1]);
               int i = 0;

               for (String s2 : list) {
                  CuboidRegion cuboidregion = this.regions.get(s2);
                  if (cuboidregion == null) {
                     this.send(sender, "no-region", new String[]{"%arena%", s2});
                  } else {
                     i += this.arenaService.snapshot(s2, cuboidregion);
                  }
               }

               this.send(sender, "snapshot-done", new String[]{"%count%", String.valueOf(i)});
               return true;
            }
         } else if (s.equals("reset") && args.length >= 2) {
            if (!sender.hasPermission("sharded.coreprotect.admin") && !sender.hasPermission("sharded.arena.admin")) {
               this.send(sender, "no-permission", new String[0]);
               return true;
            } else {
               boolean flag = args.length >= 3 && args[2].equalsIgnoreCase("fast");

               for (String s1 : this.resolveArenaIds(args[1])) {
                  if (!this.arenaService.hasSnapshot(s1)) {
                     this.send(sender, "no-snapshot", new String[]{"%arena%", s1});
                  } else {
                     this.arenaService.reset(s1, flag, () -> {
                        this.placedTracker.clearSide(s1);
                        this.placedTracker.save();
                        this.send(sender, "reset-done", new String[]{"%arena%", s1});
                     });
                  }
               }

               return true;
            }
         } else {
            this.send(sender, "arenas-usage", new String[0]);
            return true;
         }
      }
   }

   private void startAutoReset() {
      if (this.config.getBoolean("auto-reset.enabled", true)) {
         long i = this.config.getLong("auto-reset.interval-minutes", 15L);
         long j = Math.max(1200L, i * 60L * 20L);
         this.autoResetTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::runAutoReset, j, j);
      }
   }

   private void runAutoReset() {
      if (this.config.getBoolean("auto-reset.enabled", true)) {
         List<String> list = this.config.getStringList("auto-reset.commands");
         if (list.isEmpty()) {
            for (String s1 : SIDE_KEYS) {
               if (this.arenaService.hasSnapshot(s1)) {
                  this.arenaService.reset(s1, true, () -> {
                     this.placedTracker.clearSide(s1);
                     this.placedTracker.save();
                  });
               }
            }
         } else {
            for (String s : list) {
               if (s != null && !s.isBlank()) {
                  Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s);
               }
            }
         }
      }
   }

   private List<String> resolveArenaIds(String raw) {
      String s = raw.toLowerCase(Locale.ROOT);
      return !s.equals("side1-side4") && !s.equals("sides") && !s.equals("all") ? List.of(s) : SIDE_KEYS;
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      Location location;
      if (this.restrictSpawnPvp(player, location = event.getBlock().getLocation())) {
         event.setCancelled(true);
         this.send(player, "no-break", new String[0]);
      } else {
         if (this.inSide(location)) {
            if (this.bypass(player)) {
               return;
            }

            if (!this.placedTracker.isPlaced(location)) {
               event.setCancelled(true);
               this.send(player, "no-break", new String[0]);
            } else {
               this.placedTracker.unmark(location);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent event) {
      Player player = event.getPlayer();
      Location location = event.getBlock().getLocation();
      String s = this.sideAt(location);
      if (s != null) {
         if (this.bypass(player)) {
            this.placedTracker.mark(location);
         } else {
            int i = this.config.getInt("side-max-build-y", 111);
            if (location.getBlockY() > i) {
               event.setCancelled(true);
               this.send(player, "side-build-limit", new String[]{"%y%", String.valueOf(i)});
            } else {
               this.placedTracker.mark(location);
            }
         }
      } else if (this.restrictSpawnOnly(player, location)) {
         event.setCancelled(true);
         this.send(player, "no-place", new String[0]);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onPvp(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Player player) {
         if (!this.inSide(player.getLocation())) {
            if (this.inSpawn(player.getLocation())) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onMobSpawn(CreatureSpawnEvent event) {
      if (this.config.getBoolean("block-natural-mobs-in-spawn", true)) {
         if (event.getSpawnReason() == SpawnReason.NATURAL || event.getSpawnReason() == SpawnReason.JOCKEY) {
            if (this.inSpawn(event.getLocation())) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player player) {
         if (!this.inSide(player.getLocation())) {
            if (this.inSpawn(player.getLocation())) {
               if (event.getCause() == DamageCause.FALL) {
                  event.setCancelled(true);
                  return;
               }

               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = false
   )
   public void onHornUse(PlayerInteractEvent event) {
      if (this.isHornInteraction(event)) {
         if (this.hornBlocked(event.getPlayer())) {
            this.denyHorn(event, event.getPlayer());
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onHornUseGuard(PlayerInteractEvent event) {
      if (this.isHornInteraction(event)) {
         if (this.hornBlocked(event.getPlayer())) {
            this.denyHorn(event, event.getPlayer());
         }
      }
   }

   private boolean isHornInteraction(PlayerInteractEvent event) {
      ItemStack itemstack = event.getItem();
      return itemstack != null && itemstack.getType() == Material.GOAT_HORN ? event.getAction().isRightClick() : false;
   }

   private void denyHorn(PlayerInteractEvent event, Player player) {
      event.setCancelled(true);
      event.setUseItemInHand(Result.DENY);
      event.setUseInteractedBlock(Result.DENY);
      player.setCooldown(Material.GOAT_HORN, 20);
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = false
   )
   public void onHornConsume(PlayerItemConsumeEvent event) {
      if (event.getItem().getType() == Material.GOAT_HORN) {
         if (this.hornBlocked(event.getPlayer())) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onSideInteract(PlayerInteractEvent event) {
      Block block = event.getClickedBlock();
      if (block != null) {
         if (!this.sideWorlds.isEmpty()) {
            if (this.sideWorlds.contains(block.getWorld().getName().toLowerCase(Locale.ROOT))) {
               Player player = event.getPlayer();
               if (!this.bypass(player)) {
                  Location location = block.getLocation();
                  if (this.inSide(location)) {
                     if (!event.getAction().isRightClick() || event.getItem() == null || !allowsSidePlacement(event.getItem().getType())) {
                        if (SideBlockedMaterials.isBlocked(block.getType())) {
                           event.setCancelled(true);
                           event.setUseInteractedBlock(Result.DENY);
                           this.send(player, "no-use", new String[0]);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean allowsSidePlacement(Material item) {
      if (item.isBlock()) {
         return true;
      } else {
         String s = item.name();
         return s.endsWith("_SEEDS") || item == Material.BONE_MEAL || item == Material.NETHER_WART;
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onInteract(PlayerInteractEvent event) {
      Block block = event.getClickedBlock();
      if (block != null) {
         Player player = event.getPlayer();
         if (!this.bypass(player)) {
            Location location = block.getLocation();
            if (this.sideWorlds.isEmpty() || !this.sideWorlds.contains(location.getWorld().getName().toLowerCase(Locale.ROOT)) || !this.inSide(location)) {
               if (this.inSpawn(location) || this.inPvp(location)) {
                  Material material = block.getType();
                  if (SPAWN_BLOCKED_USE.contains(material) || material.name().endsWith("_TRAPDOOR")) {
                     event.setCancelled(true);
                     this.send(player, "no-use", new String[0]);
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onPearl(PlayerTeleportEvent event) {
      if (event.getCause() == TeleportCause.ENDER_PEARL) {
         Player player = event.getPlayer();
         if (player instanceof Player) {
            Location location = event.getTo();
            if (location != null && !this.bypass(player) && !this.inSide(location)) {
               if (this.inSpawn(location)) {
                  event.setCancelled(true);
                  this.send(player, "no-pearl-spawn", new String[0]);
               }
            }
         }
      }
   }

   private void saveConfig() {
      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (Exception exception) {
         this.plugin.getLogger().warning("[coreprotect] Could not save config: " + exception.getMessage());
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      return List.of();
   }
}
