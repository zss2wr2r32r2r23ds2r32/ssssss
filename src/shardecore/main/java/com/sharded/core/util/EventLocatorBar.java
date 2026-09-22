package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.koth.KothModule;
import com.sharded.core.modules.outpost.OutpostModule;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class EventLocatorBar implements Listener {
   private static final String TEAM_PREFIX = "sc_loc_";
   private static EventLocatorBar instance;
   private final ShardedCore plugin;
   private final NamespacedKey markerKey;
   private final Map<String, ArmorStand> markers = new HashMap<>();
   private final Map<String, String> letters = new HashMap<>();
   private final Map<String, Boolean> capturing = new HashMap<>();
   private final Map<UUID, Boolean> lastTransmit = new HashMap<>();
   private final Map<UUID, Boolean> lastReceive = new HashMap<>();
   private List<String> cachedSpawnWorlds = List.of("spawn");
   private long spawnWorldsCachedAt;
   private Location cachedOutpostCenter;
   private Location cachedKothCenter;
   private long centersCachedAt;
   private boolean idleCleaned;
   private int taskId = -1;
   private boolean pendingIazip;
   private boolean iazipScheduled;
   private boolean eventsActive;

   private EventLocatorBar(ShardedCore plugin) {
      this.plugin = plugin;
      this.markerKey = new NamespacedKey(plugin, "event_locator");
   }

   public static EventLocatorBar get() {
      return instance;
   }

   public static void refresh() {
      if (instance != null) {
         instance.tick();
      }
   }

   public static void install(ShardedCore plugin) {
      if (instance != null) {
         instance.shutdown();
      }

      instance = new EventLocatorBar(plugin);
      instance.exportResourcePack();
      instance.injectItemsAdder();
      plugin.getServer().getPluginManager().registerEvents(instance, plugin);
      instance.start();
      instance.syncPlayerVisibility();
      plugin.getServer().getScheduler().runTaskLater(plugin, instance::injectItemsAdder, 40L);
      plugin.getServer().getScheduler().runTaskLater(plugin, instance::injectItemsAdder, 120L);
   }

   private void exportResourcePack() {
      File file1 = new File(this.plugin.getDataFolder(), "locator-bar-pack");
      this.copyPackFile("pack.mcmeta", file1);

      for (String s : ItemsAdderLocatorPack.ASSET_FILES) {
         this.copyPackFile(s, file1);
      }
   }

   private void copyPackFile(String relative, File dest) {
      try {
         try (InputStream inputstream = this.plugin.getResource("locator-bar-pack/" + relative)) {
            if (inputstream != null) {
               File file1 = new File(dest, relative);
               if (file1.getParentFile() != null) {
                  file1.getParentFile().mkdirs();
               }

               Files.copy(inputstream, file1.toPath(), StandardCopyOption.REPLACE_EXISTING);
               return;
            }
         }
      } catch (Exception exception) {
      }
   }

   @EventHandler
   public void onPluginEnable(PluginEnableEvent event) {
      if ("ItemsAdder".equals(event.getPlugin().getName())) {
         this.injectItemsAdder();
      }
   }

   private void injectItemsAdder() {
      File file1 = this.plugin.getDataFolder().getParentFile();
      File file2 = new File(file1, "ItemsAdder");
      Plugin plugin = Bukkit.getPluginManager().getPlugin("ItemsAdder");
      if (plugin != null || file2.isDirectory()) {
         try {
            boolean flag = ItemsAdderLocatorPack.sync(file2, this.plugin::getResource);
            if (flag) {
               this.pendingIazip = true;
               this.plugin
                  .getLogger()
                  .info("[locator] Wrote Outpost/KOTH icons into ItemsAdder contents/sharded_locator so they ship in the ItemsAdder resource pack.");
            }
         } catch (Exception exception) {
            this.plugin.getLogger().warning("[locator] Could not copy locator icons into ItemsAdder: " + exception.getMessage());
         }

         this.scheduleIazip();
      }
   }

   private void scheduleIazip() {
      if (this.pendingIazip && !this.iazipScheduled && ItemsAdderHook.isAvailable()) {
         this.iazipScheduled = true;
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (this.pendingIazip && ItemsAdderHook.isAvailable()) {
               this.pendingIazip = false;
               this.plugin.getLogger().info("[locator] Rebuilding the ItemsAdder resource pack (/iazip).");
               Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iazip");
            }
         }, 40L);
      }
   }

   public static void shutdownActive() {
      if (instance != null) {
         instance.shutdown();
         instance = null;
      }
   }

   private void start() {
      this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, this::tick, 20L, 20L);
   }

   private void shutdown() {
      if (this.taskId >= 0) {
         Bukkit.getScheduler().cancelTask(this.taskId);
         this.taskId = -1;
      }

      for (String s : new HashMap<>(this.markers).keySet()) {
         this.hide(s);
      }

      this.eventsActive = false;

      for (Player player : Bukkit.getOnlinePlayers()) {
         this.setPlayerTransmit(player, true);
         this.setPlayerReceive(player, true);
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      this.syncOne(event.getPlayer());
   }

   @EventHandler
   public void onWorld(PlayerChangedWorldEvent event) {
      this.syncOne(event.getPlayer());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.lastTransmit.remove(event.getPlayer().getUniqueId());
      this.lastReceive.remove(event.getPlayer().getUniqueId());
      this.setPlayerTransmit(event.getPlayer(), true);
      this.setPlayerReceive(event.getPlayer(), true);
   }

   private void tick() {
      long now = System.currentTimeMillis();
      if (!this.eventsActive && this.idleCleaned && now - this.centersCachedAt < 5000L) {
         return;
      }
      if (now - this.centersCachedAt > 30000L) {
         this.cachedOutpostCenter = this.regionCenter(new File(this.plugin.getDataFolder(), "modules/outpost/config.yml"));
         this.cachedKothCenter = this.regionCenter(new File(this.plugin.getDataFolder(), "modules/koth/config.yml"));
         this.centersCachedAt = now;
      }

      OutpostModule outpostmodule = this.plugin.modules().get(OutpostModule.class);
      boolean flag = outpostmodule != null && outpostmodule.isActive();
      if (flag) {
         Location location = outpostmodule.regionCenter();
         if (location == null) {
            location = this.cachedOutpostCenter;
         }
         if (location != null) {
            boolean flag1 = outpostmodule.capturePercent() > 0.0 && !"N/A".equals(outpostmodule.capturerName());
            this.show("outpost", location, "O", flag1);
            this.idleCleaned = false;
            this.enableLocatorBar(location.getWorld());
         } else {
            this.hide("outpost");
            flag = false;
         }
      } else {
         this.hide("outpost");
      }

      KothModule kothmodule = this.plugin.modules().get(KothModule.class);
      boolean flag2 = kothmodule != null && kothmodule.isActive();
      if (flag2) {
         Location location1 = kothmodule.regionCenter();
         if (location1 == null) {
            location1 = this.cachedKothCenter;
         }
         if (location1 != null) {
            this.show("koth", location1, "K", this.kothCapturing(kothmodule));
            this.idleCleaned = false;
            this.enableLocatorBar(location1.getWorld());
         } else {
            this.hide("koth");
            flag2 = false;
         }
      } else {
         this.hide("koth");
      }

      this.eventsActive = flag || flag2;
      if (this.eventsActive) {
         for (World world : Bukkit.getWorlds()) {
            this.enableLocatorBar(world);
         }
      }
      if (!this.eventsActive && !this.idleCleaned) {
         this.purgeLeftoverMarkers();
         this.idleCleaned = true;
      }

      this.syncPlayerVisibility();
   }

   private boolean kothCapturing(KothModule koth) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (koth.isInside(player)) {
            return true;
         }
      }

      String s = koth.leaderName();
      return s != null && !s.isBlank() && !s.equalsIgnoreCase("N/A") && !s.equalsIgnoreCase("None");
   }

   public void show(String id, Location location, String letter, boolean capturingNow) {
      if (location != null && location.getWorld() != null) {
         ArmorStand armorstand = this.markers.get(id);
         if (armorstand != null && armorstand.isValid()) {
            if (armorstand.getWorld() != location.getWorld() || armorstand.getLocation().distanceSquared(location) > 4.0) {
               armorstand.teleport(location);
            }
         } else {
            armorstand = this.spawn(id, location);
            this.markers.put(id, armorstand);
            this.letters.remove(id);
            this.capturing.remove(id);
            ArmorStand spawned = armorstand;
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
               if (spawned.isValid()) {
                  this.applyStyle(spawned, id, letter, capturingNow);
               }
            });
         }

         String s = this.letters.put(id, letter);
         Boolean obool = this.capturing.put(id, capturingNow);
         if (!letter.equals(s) || obool == null || obool != capturingNow) {
            this.applyStyle(armorstand, id, letter, capturingNow);
         }
      }
   }

   public void hide(String id) {
      ArmorStand armorstand = this.markers.remove(id);
      this.letters.remove(id);
      this.capturing.remove(id);
      if (armorstand != null && armorstand.isValid()) {
         this.disableWaypoint(armorstand);
         armorstand.remove();
      }

      this.removeTeam(id);
      this.purgeMarkerId(id);
   }

   private ArmorStand spawn(String id, Location location) {
      return (ArmorStand)location.getWorld().spawn(location, ArmorStand.class, stand -> {
         stand.setInvisible(true);
         stand.setMarker(false);
         stand.setSmall(true);
         stand.setGravity(false);
         stand.setSilent(true);
         stand.setInvulnerable(true);
         stand.setCollidable(false);
         stand.setPersistent(false);
         stand.setCustomNameVisible(false);
         stand.setCanPickupItems(false);
         stand.addScoreboardTag("sharded_locator_" + id);
         stand.getPersistentDataContainer().set(this.markerKey, PersistentDataType.STRING, id);
         AttributeInstance attributeinstance = stand.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE);
         if (attributeinstance != null) {
            attributeinstance.setBaseValue(6.0E7);
         }
      });
   }

   private void syncPlayerVisibility() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.syncOne(player);
      }
   }

   private void syncOne(Player player) {
      boolean flag = this.inSpawnWorld(player);
      this.setPlayerTransmit(player, !flag);
      this.setPlayerReceive(player, this.eventsActive || !flag);
   }

   private void setPlayerTransmit(Player player, boolean visible) {
      Boolean previous = this.lastTransmit.put(player.getUniqueId(), visible);
      if (previous != null && previous == visible) {
         return;
      }
      AttributeInstance attributeinstance = player.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE);
      if (attributeinstance != null) {
         attributeinstance.setBaseValue(visible ? attributeinstance.getDefaultValue() : 0.0);
      }
   }

   private void enableLocatorBar(World world) {
      if (world != null) {
         try {
            org.bukkit.GameRule<?> rule = org.bukkit.GameRule.getByName("locatorBar");
            if (rule != null) {
               world.setGameRule((org.bukkit.GameRule<Boolean>) rule, Boolean.TRUE);
            }
         } catch (Throwable ignored) {
         }
      }
   }

   private void setPlayerReceive(Player player, boolean visible) {
      Boolean previous = this.lastReceive.put(player.getUniqueId(), visible);
      if (previous != null && previous == visible) {
         return;
      }
      AttributeInstance attributeinstance = player.getAttribute(Attribute.WAYPOINT_RECEIVE_RANGE);
      if (attributeinstance != null) {
         attributeinstance.setBaseValue(visible ? 6.0E7 : 0.0);
      }
   }

   private void disableWaypoint(ArmorStand stand) {
      AttributeInstance attributeinstance = stand.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE);
      if (attributeinstance != null) {
         attributeinstance.setBaseValue(0.0);
      }
   }

   private void purgeLeftoverMarkers() {
      this.purgeMarkerId("outpost");
      this.purgeMarkerId("koth");
   }

   private void purgeMarkerId(String id) {
      ArmorStand known = this.markers.get(id);
      if (known != null && known.isValid()) {
         return;
      }
      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
            String s = (String)entity.getPersistentDataContainer().get(this.markerKey, PersistentDataType.STRING);
            if (id.equals(s) && !this.markers.containsValue(entity)) {
               this.disableWaypoint((ArmorStand) entity);
               entity.remove();
            }
         }
      }
   }

   private boolean inSpawnWorld(Player player) {
      if (player.getWorld() == null) {
         return false;
      } else {
         String s = player.getWorld().getName();

         for (String s1 : this.spawnWorlds()) {
            if (s1.equalsIgnoreCase(s)) {
               return true;
            }
         }

         return false;
      }
   }

   private List<String> spawnWorlds() {
      long now = System.currentTimeMillis();
      if (now - this.spawnWorldsCachedAt < 10000L && this.cachedSpawnWorlds != null) {
         return this.cachedSpawnWorlds;
      }
      List<String> list = new ArrayList<>();
      File file1 = new File(this.plugin.getDataFolder(), "modules/spawnselect/config.yml");
      if (file1.isFile()) {
         YamlConfiguration yamlconfiguration = YamlConfiguration.loadConfiguration(file1);
         String s = yamlconfiguration.getString("join-world", "spawn");
         if (s != null && !s.isBlank()) {
            list.add(s);
         }
      }

      File file2 = new File(this.plugin.getDataFolder(), "modules/outpost/config.yml");
      if (file2.isFile()) {
         YamlConfiguration yamlconfiguration1 = YamlConfiguration.loadConfiguration(file2);
         list.addAll(yamlconfiguration1.getStringList("allowed-worlds"));
      }

      if (list.isEmpty()) {
         list.add("spawn");
      }

      this.cachedSpawnWorlds = list;
      this.spawnWorldsCachedAt = now;
      return list;
   }

   private void applyStyle(ArmorStand stand, String id, String letter, boolean capturingNow) {
      Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
      String s = ("sc_loc_" + id).toLowerCase(Locale.ROOT);
      if (s.length() > 16) {
         s = s.substring(0, 16);
      }

      Team team = scoreboard.getTeam(s);
      if (team == null) {
         team = scoreboard.registerNewTeam(s);
      }

      team.setColor(capturingNow ? ChatColor.RED : ChatColor.WHITE);
      team.addEntity(stand);
      String s1 = "minecraft:event_" + letter.toLowerCase(Locale.ROOT);
      String s2 = "@e[tag=sharded_locator_" + id + ",limit=1]";
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "waypoint modify " + s2 + " style set " + s1);
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "waypoint modify " + s2 + " color " + (capturingNow ? "red" : "white"));
   }

   private void removeTeam(String id) {
      Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
      String s = ("sc_loc_" + id).toLowerCase(Locale.ROOT);
      if (s.length() > 16) {
         s = s.substring(0, 16);
      }

      Team team = scoreboard.getTeam(s);
      if (team != null) {
         team.unregister();
      }
   }

   private Location regionCenter(File file) {
      if (!file.isFile()) {
         return null;
      } else {
         YamlConfiguration yamlconfiguration = YamlConfiguration.loadConfiguration(file);
         CuboidRegion cuboidregion = CuboidRegion.fromSection(yamlconfiguration.getConfigurationSection("region"));
         if (cuboidregion == null) {
            return null;
         } else {
            World world = cuboidregion.bukkitWorld();
            if (world == null) {
               return null;
            } else {
               double d0 = (double)(cuboidregion.minX() + cuboidregion.maxX() + 1) / 2.0;
               double d1 = (double)cuboidregion.minY() + 1.0;
               double d2 = (double)(cuboidregion.minZ() + cuboidregion.maxZ() + 1) / 2.0;
               return new Location(world, d0, d1, d2);
            }
         }
      }
   }

   public static void purgeLeftovers(ShardedCore plugin) {
      NamespacedKey namespacedkey = new NamespacedKey(plugin, "event_locator");

      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (entity.getPersistentDataContainer().has(namespacedkey, PersistentDataType.STRING)) {
               entity.remove();
            }
         }
      }
   }
}
