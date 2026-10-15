package com.sharded.core.modules.portalrtp;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.duel.DuelModule;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class PortalRtpModule extends Module implements CommandExecutor, TabCompleter {
   private final Map<UUID, Long> portalGuiCooldown = new ConcurrentHashMap<>();
   private final Map<UUID, Long> rtpCooldown = new ConcurrentHashMap<>();
   private final Map<UUID, PortalRtpModule.Pending> pending = new ConcurrentHashMap<>();
   private final Map<UUID, Long> safeLanding = new ConcurrentHashMap<>();
   private final Set<String> unlocked = ConcurrentHashMap.newKeySet();
   private final Map<String, PortalRtpModule.Destination> destinations = new LinkedHashMap<>();
   private final Map<String, Deque<Location>> pools = new ConcurrentHashMap<>();
   private final List<PortalRtpModule.Search> searches = new ArrayList<>();
   private final Set<UUID> queue = new LinkedHashSet<>();
   private final Map<UUID, UUID> queueRequests = new ConcurrentHashMap<>();
   private final Map<UUID, BukkitTask> queueWaitTasks = new ConcurrentHashMap<>();
   private PortalTriggerStore triggers;
   private String portalWorldName = "spawn";
   private BukkitTask searchTask;
   private BukkitTask refillTask;
   private long generateWindowStart;
   private int generateUsed;
   private boolean refillRequested;

   public PortalRtpModule(ShardedCore plugin) {
      super(plugin, "portalrtp");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("rtp", this);
      this.registerCommand("unlock", this);
      this.registerCommand("lock", this);
      this.registerCommand("rtpqueue", this);
      this.registerCommand("leave", this);
      this.ensureLockedDisplay();
      this.triggers = new PortalTriggerStore(this.plugin, this.moduleFolder());
      this.portalWorldName = this.config.getString("portal-world", "spawn");
      this.loadDestinations();
      this.loadUnlockState();
      this.registerGuiActions();
      long i = Math.max(20L, this.config.getLong("refill-seconds", 10L) * 20L);
      long searchInterval = Math.max(1L, this.config.getLong("search-interval-ticks", 5L));
      this.searchTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> this.tickSearches(), searchInterval, searchInterval);
      this.refillTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> this.refillPools(), 100L, i);
   }

   @Override
   protected void onDisable() {
      if (this.searchTask != null) {
         this.searchTask.cancel();
         this.searchTask = null;
      }

      if (this.refillTask != null) {
         this.refillTask.cancel();
         this.refillTask = null;
      }

      for (UUID uuid : new ArrayList<>(this.pending.keySet())) {
         this.cancelPending(uuid, false);
      }

      this.pending.clear();
      this.searches.clear();
      this.pools.clear();
      this.queue.clear();
      this.queueRequests.clear();
      this.cancelAllQueueWaitTasks();
      this.saveUnlockState();
   }

   private void registerGuiActions() {
      this.plugin.gui().registerAction("rtp_menu", this::openMenu);
      this.plugin.gui().registerAction("rtp_queue", this::openDuelQueue);
      this.plugin.gui().registerAction("duel_queue", this::openDuelQueue);
      this.plugin.gui().registerAction("rtp_confirm", player -> this.teleportTo(player, this.defaultDestination()));

      for (String s : List.of("overworld", "nether", "end")) {
         this.plugin.gui().registerAction("rtp_" + s, player -> this.teleportTo(player, this.destinations.get(s)));
      }
   }

   private void teleportTo(Player player, PortalRtpModule.Destination dest) {
      if (dest == null) {
         this.chat(player, "world-missing");
      } else {
         this.beginTeleport(player, dest, this.config.getInt("countdown-seconds", 5), null);
      }
   }

   private void loadDestinations() {
      this.destinations.clear();
      ConfigurationSection configurationsection = this.config.getConfigurationSection("worlds");
      if (configurationsection != null) {
         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            if (configurationsection1 != null) {
               this.destinations.put(s.toLowerCase(Locale.ROOT), new PortalRtpModule.Destination(s.toLowerCase(Locale.ROOT), configurationsection1));
            }
         }
      }
   }

   private void loadUnlockState() {
      this.unlocked.clear();

      for (PortalRtpModule.Destination portalrtpmodule$destination : this.destinations.values()) {
         if (!portalrtpmodule$destination.lockedByDefault) {
            this.unlocked.add(portalrtpmodule$destination.id);
         }
      }

      File file1 = new File(this.moduleFolder(), "unlocks.yml");
      if (file1.exists()) {
         YamlConfiguration yamlconfiguration = YamlConfiguration.loadConfiguration(file1);
         if (yamlconfiguration.contains("unlocked")) {
            this.unlocked.clear();

            for (String s : yamlconfiguration.getStringList("unlocked")) {
               if (s != null) {
                  this.unlocked.add(s.toLowerCase(Locale.ROOT));
               }
            }
         }
      }
   }

   private void saveUnlockState() {
      YamlConfiguration yamlconfiguration = new YamlConfiguration();
      yamlconfiguration.set("unlocked", new ArrayList<>(this.unlocked));

      try {
         yamlconfiguration.save(new File(this.moduleFolder(), "unlocks.yml"));
      } catch (Exception exception) {
      }
   }

   private boolean isUnlocked(PortalRtpModule.Destination dest) {
      return dest != null && this.unlocked.contains(dest.id);
   }

   public String targetWorldName() {
      PortalRtpModule.Destination portalrtpmodule$destination = this.defaultDestination();
      return portalrtpmodule$destination == null ? this.config.getString("target-world", "world") : portalrtpmodule$destination.worldName;
   }

   public Location findSafeLocation(World world) {
      if (world == null) {
         return null;
      } else {
         for (PortalRtpModule.Destination portalrtpmodule$destination : this.destinations.values()) {
            if (portalrtpmodule$destination.worldName.equalsIgnoreCase(world.getName())) {
               Location location = this.pollPool(portalrtpmodule$destination);
               if (location != null) {
                  return location;
               }

               return this.searchBlocking(portalrtpmodule$destination, world);
            }
         }

         return null;
      }
   }

   private PortalRtpModule.Destination defaultDestination() {
      PortalRtpModule.Destination portalrtpmodule$destination = this.destinations.get("overworld");
      return portalrtpmodule$destination != null ? portalrtpmodule$destination : this.destinations.values().stream().findFirst().orElse(null);
   }

   @EventHandler
   public void onPortalEnter(EntityPortalEnterEvent event) {
      if (event.getEntity() instanceof Player player) {
         Location loc = event.getLocation().getBlock().getLocation();
         if (this.onTriggerBlock(loc)) {
            this.tryOpen(player, loc);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPortal(PlayerPortalEvent event) {
      if (event.getCause() == TeleportCause.NETHER_PORTAL
         && event.getFrom().getWorld() != null
         && event.getFrom().getWorld().getName().equalsIgnoreCase(this.portalWorld())) {
         event.setCancelled(true);
      }
   }

   private boolean onTriggerBlock(Location location) {
      if (location != null && location.getWorld() != null && this.triggers != null) {
         if (!location.getWorld().getName().equalsIgnoreCase(this.portalWorldName)) {
            return false;
         } else {
            Location locationx = location.getBlock().getLocation();
            return this.triggers.isTrigger(locationx) || this.triggers.isTrigger(locationx.clone().subtract(0.0, 1.0, 0.0));
         }
      } else {
         return false;
      }
   }

   private void tryOpen(Player player, Location at) {
      if (player.getWorld().getName().equalsIgnoreCase(this.portalWorld())) {
         if (player.hasPermission("sharded.rtp.use") && this.triggers.isTrigger(at)) {
            long i = System.currentTimeMillis();
            Long olong = this.portalGuiCooldown.get(player.getUniqueId());
            if (olong == null || i - olong >= this.config.getLong("gui-cooldown-ms", 2000L)) {
               this.portalGuiCooldown.put(player.getUniqueId(), i);
               this.openMenu(player);
            }
         }
      }
   }

   private String portalWorld() {
      return this.portalWorldName;
   }

   private boolean canUseRtp(Player player) {
      if (player.hasPermission("sharded.rtp.bypass")) {
         return true;
      } else {
         return !this.config.getBoolean("require-portal-world", true) ? true : player.getWorld().getName().equalsIgnoreCase(this.portalWorld());
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);
      if (s.equals("unlock") || s.equals("rtpunlock")) {
         return this.handleLockCommand(sender, args, true);
      } else if (s.equals("lock") || s.equals("rtplock")) {
         return this.handleLockCommand(sender, args, false);
      } else if (sender instanceof Player player) {
         if (s.equals("leave")) {
            this.leaveQueue(player);
            return true;
         } else if (s.equals("rtpqueue")) {
            this.handleQueueCommand(player, args);
            return true;
         } else if (!player.hasPermission("sharded.rtp.use")) {
            this.chat(player, "no-permission");
            return true;
         } else if (!this.canUseRtp(player)) {
            this.chat(player, "wrong-world", "%world%", this.portalWorld());
            return true;
         } else {
            this.openMenu(player);
            return true;
         }
      } else {
         this.chat(sender, "players-only");
         return true;
      }
   }

   private boolean handleLockCommand(CommandSender sender, String[] args, boolean unlock) {
      if (!sender.hasPermission("sharded.rtp.admin")) {
         this.chat(sender, "no-permission");
         return true;
      } else if (args.length == 0) {
         this.chat(sender, unlock ? "unlock-usage" : "lock-usage");
         return true;
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);
         PortalRtpModule.Destination portalrtpmodule$destination = this.destinations.get(s);
         if (portalrtpmodule$destination == null) {
            this.chat(sender, "unknown-destination", "%destination%", s);
            return true;
         } else {
            if (unlock) {
               this.unlocked.add(s);
            } else {
               this.unlocked.remove(s);
               this.pools.remove(s);
            }

            this.saveUnlockState();
            this.chat(sender, unlock ? "was-unlocked" : "was-locked", "%display%", portalrtpmodule$destination.display);
            if (unlock) {
               String s1 = this.message("announce-unlocked", "%display%", portalrtpmodule$destination.display);
               if (s1 != null) {
                  for (Player player : Bukkit.getOnlinePlayers()) {
                     player.sendMessage(Text.c(s1));
                  }
               }
            }

            return true;
         }
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);
      if (!s.equals("unlock") && !s.equals("lock") && !s.equals("rtpunlock") && !s.equals("rtplock")) {
         if (s.equals("rtpqueue") && args.length == 1) {
            List<String> list = new ArrayList<>(List.of("accept", "leave"));

            for (Player player : Bukkit.getOnlinePlayers()) {
               list.add(player.getName());
            }

            return TabCompleteHelper.filter(args[0], list);
         } else {
            return List.of();
         }
      } else {
         return sender.hasPermission("sharded.rtp.admin") && args.length == 1 ? TabCompleteHelper.filter(args[0], this.destinations.keySet()) : List.of();
      }
   }

   private void openMenu(Player player) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("menu");
      int i = configurationsection == null ? 3 : Math.max(1, Math.min(6, configurationsection.getInt("rows", 3)));
      String s = configurationsection == null ? "&8Random Teleport" : configurationsection.getString("title", "&8Random Teleport");
      PortalRtpModule.MenuHolder portalrtpmodule$menuholder = new PortalRtpModule.MenuHolder();
      Inventory inventory = Bukkit.createInventory(portalrtpmodule$menuholder, i * 9, Text.c(s));
      portalrtpmodule$menuholder.inventory = inventory;
      ItemStack itemstack = this.fillerItem(configurationsection);
      if (itemstack != null) {
         for (int j = 0; j < inventory.getSize(); j++) {
            inventory.setItem(j, itemstack);
         }
      }

      for (PortalRtpModule.Destination portalrtpmodule$destination : this.destinations.values()) {
         if (portalrtpmodule$destination.slot >= 0 && portalrtpmodule$destination.slot < inventory.getSize()) {
            inventory.setItem(portalrtpmodule$destination.slot, this.destinationItem(portalrtpmodule$destination));
         }
      }

      ConfigurationSection configurationsection1 = configurationsection == null ? null : configurationsection.getConfigurationSection("queue");
      if (configurationsection1 != null) {
         int k = configurationsection1.getInt("slot", -1);
         if (k >= 0 && k < inventory.getSize()) {
            inventory.setItem(
               k,
               this.item(
                  this.material(configurationsection1.getString("material"), Material.DIAMOND_SWORD),
                  configurationsection1.getString("name", "&bRTP Queue"),
                  configurationsection1.getStringList("lore")
               )
            );
         }
      }

      player.openInventory(inventory);
      this.playSound(player, "menu-open");
   }

   private ItemStack fillerItem(ConfigurationSection menu) {
      ConfigurationSection configurationsection = menu == null ? null : menu.getConfigurationSection("filler");
      if (configurationsection == null) {
         return null;
      } else {
         Material material = this.material(configurationsection.getString("material"), Material.BLACK_STAINED_GLASS_PANE);
         return material == Material.AIR ? null : this.item(material, configurationsection.getString("name", " "), configurationsection.getStringList("lore"));
      }
   }

   private void ensureLockedDisplay() {
      if (this.config.getInt("locked-display-version", 0) >= 2) {
         return;
      }
      try (InputStream inputstream = this.plugin.getResource(this.jarResourcePath("config.yml"))) {
         if (inputstream != null) {
            YamlConfiguration yamlconfiguration = YamlConfiguration.loadConfiguration(new InputStreamReader(inputstream, StandardCharsets.UTF_8));
            ConfigurationSection configurationsection = yamlconfiguration.getConfigurationSection("locked-display");
            if (configurationsection != null) {
               this.config.set("locked-display", configurationsection);
            }
         }
      } catch (Exception exception) {
         this.plugin.getLogger().warning("[portalrtp] Could not copy locked-display: " + exception.getMessage());
      }
      this.config.set("locked-display-version", 2);
      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (Exception exception) {
         this.plugin.getLogger().warning("[portalrtp] Could not save locked-display: " + exception.getMessage());
      }
   }

   private ItemStack destinationItem(PortalRtpModule.Destination dest) {
      if (!this.isUnlocked(dest)) {
         ConfigurationSection configurationsection = this.config.getConfigurationSection("locked-display");
         Material material = configurationsection == null
            ? Material.RED_STAINED_GLASS_PANE
            : this.material(configurationsection.getString("material"), Material.RED_STAINED_GLASS_PANE);
         String s = configurationsection == null ? "&c&l%display% (LOCKED)" : configurationsection.getString("name", "&c&l%display%");
         List<String> list = configurationsection == null ? List.of() : configurationsection.getStringList("lore");
         return this.item(material, s.replace("%display%", dest.display), this.applyPlaceholders(list, dest));
      } else {
         return this.item(dest.material, dest.name, this.applyPlaceholders(dest.lore, dest));
      }
   }

   private List<String> applyPlaceholders(List<String> lines, PortalRtpModule.Destination dest) {
      List<String> list = new ArrayList<>(lines.size());
      String s = compact((long)dest.radius);
      String s1 = compact((long)dest.radius * 2L) + " x " + compact((long)dest.radius * 2L);
      World world = Bukkit.getWorld(dest.worldName);
      int i = world == null ? 0 : world.getPlayers().size();

      for (String s2 : lines) {
         list.add(s2.replace("%display%", dest.display).replace("%players%", String.valueOf(i)).replace("%radius%", s).replace("%border%", s1));
      }

      return list;
   }

   private ItemStack item(Material material, String name, List<String> lore) {
      ItemStack itemstack = new ItemStack(material);
      ItemMeta itemmeta = itemstack.getItemMeta();
      if (itemmeta != null) {
         if (name != null) {
            itemmeta.displayName(Text.c(name));
         }

         if (lore != null && !lore.isEmpty()) {
            List<Component> list = new ArrayList<>(lore.size());

            for (String s : lore) {
               list.add(Text.c(s));
            }

            itemmeta.lore(list);
         }

         itemstack.setItemMeta(itemmeta);
      }

      return itemstack;
   }

   private Material material(String raw, Material fallback) {
      if (raw != null && !raw.isBlank()) {
         Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
         return material == null ? fallback : material;
      } else {
         return fallback;
      }
   }

   private static String compact(long value) {
      if (value >= 1000000L) {
         double d1 = (double)value / 1000000.0;
         return (d1 == Math.floor(d1) ? String.valueOf((long)d1) : String.format(Locale.US, "%.1f", d1)) + "M";
      } else if (value >= 1000L) {
         double d0 = (double)value / 1000.0;
         return (d0 == Math.floor(d0) ? String.valueOf((long)d0) : String.format(Locale.US, "%.1f", d0)) + "K";
      } else {
         return String.valueOf(value);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onMenuClick(InventoryClickEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof PortalRtpModule.MenuHolder) {
         event.setCancelled(true);
         if (event.getClickedInventory() == event.getView().getTopInventory()) {
            if (event.getWhoClicked() instanceof Player player) {
               int i = event.getSlot();
               ConfigurationSection configurationsection = this.config.getConfigurationSection("menu");
               ConfigurationSection configurationsection1 = configurationsection == null ? null : configurationsection.getConfigurationSection("queue");
               if (configurationsection1 != null && configurationsection1.getInt("slot", -1) == i) {
                  player.closeInventory();
                  this.openDuelQueue(player);
               } else {
                  for (PortalRtpModule.Destination portalrtpmodule$destination : this.destinations.values()) {
                     if (portalrtpmodule$destination.slot == i) {
                        player.closeInventory();
                        this.beginTeleport(player, portalrtpmodule$destination, this.config.getInt("countdown-seconds", 5), null);
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   private void beginTeleport(Player player, PortalRtpModule.Destination dest, int countdownSeconds, Runnable afterTeleport) {
      if (!player.hasPermission("sharded.rtp.use")) {
         this.chat(player, "no-permission");
         this.playSound(player, "error");
      } else if (!this.isUnlocked(dest)) {
         this.chat(player, "locked", "%display%", dest.display);
         this.playSound(player, "error");
      } else if (this.pending.containsKey(player.getUniqueId())) {
         this.chat(player, "already-teleporting");
         this.playSound(player, "error");
      } else {
         if (!player.hasPermission("sharded.rtp.bypass")) {
            Long olong = this.rtpCooldown.get(player.getUniqueId());
            long i = System.currentTimeMillis();
            if (olong != null && olong > i) {
               this.chat(player, "cooldown", "%time%", String.valueOf(Math.max(1L, (olong - i) / 1000L)));
               this.playSound(player, "error");
               return;
            }
         }

         World world = Bukkit.getWorld(dest.worldName);
         if (world == null) {
            this.chat(player, "world-missing");
            this.playSound(player, "error");
         } else {
            double d0 = this.cost();
            if (d0 > 0.0 && !PortalRtpModule.Economy.has(player, d0)) {
               this.chat(player, "cannot-afford", "%amount%", compact((long)d0));
               this.playSound(player, "error");
            } else {
               PortalRtpModule.Pending portalrtpmodule$pending = new PortalRtpModule.Pending(player.getLocation().clone(), dest, afterTeleport);
               this.pending.put(player.getUniqueId(), portalrtpmodule$pending);
               Location location = this.pollPool(dest);
               if (location != null) {
                  portalrtpmodule$pending.target = location;
               } else {
                  this.announce(player, "searching");
                  this.request(dest, world, found -> {
                     PortalRtpModule.Pending portalrtpmodule$pending1 = this.pending.get(player.getUniqueId());
                     if (portalrtpmodule$pending1 == portalrtpmodule$pending) {
                        portalrtpmodule$pending.target = found;
                        portalrtpmodule$pending.searchFinished = true;
                     }
                  });
               }

               int[] aint = new int[]{Math.max(0, countdownSeconds)};
               portalrtpmodule$pending.task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
                  if (!player.isOnline()) {
                     this.cancelPending(player.getUniqueId(), false);
                  } else if (aint[0] <= 0) {
                     if (portalrtpmodule$pending.target == null && !portalrtpmodule$pending.searchFinished) {
                        this.announce(player, "searching");
                     } else {
                        this.cancelPending(player.getUniqueId(), false);
                        this.finishTeleport(player, portalrtpmodule$pending);
                     }
                  } else {
                     this.announce(player, "countdown", "%seconds%", String.valueOf(aint[0]));
                     this.playSound(player, "countdown");
                     aint[0]--;
                  }
               }, 0L, 20L);
            }
         }
      }
   }

   private void finishTeleport(Player player, PortalRtpModule.Pending state) {
      Location location = state.target;
      if (location == null) {
         this.chat(player, "not-found");
         this.playSound(player, "error");
      } else {
         double d0 = this.cost();
         if (d0 > 0.0 && !PortalRtpModule.Economy.withdraw(player, d0)) {
            this.chat(player, "cannot-afford", "%amount%", compact((long)d0));
            this.playSound(player, "error");
         } else {
            if (!player.hasPermission("sharded.rtp.bypass")) {
               this.rtpCooldown.put(player.getUniqueId(), System.currentTimeMillis() + this.config.getLong("cooldown-seconds", 10L) * 1000L);
            }

            player.teleportAsync(location, TeleportCause.PLUGIN).thenAccept(success -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                  if (player.isOnline()) {
                     if (Boolean.TRUE.equals(success)) {
                        this.applySafeLanding(player);
                        this.announce(player, "teleported");
                        this.playSound(player, "teleport");
                        if (state.afterTeleport != null) {
                           state.afterTeleport.run();
                        }
                     } else {
                        this.chat(player, "not-found");
                        this.playSound(player, "error");
                     }
                  }
               }));
         }
      }
   }

   private void applySafeLanding(Player player) {
      int i = this.config.getInt("safe-landing-seconds", 5);
      if (i > 0) {
         this.safeLanding.put(player.getUniqueId(), System.currentTimeMillis() + (long)i * 1000L);
         player.setFallDistance(0.0F);
         player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, i * 20, 0, false, false, true));
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onFallDamage(EntityDamageEvent event) {
      if (event.getCause() == DamageCause.FALL && event.getEntity() instanceof Player player) {
         Long olong = this.safeLanding.get(player.getUniqueId());
         if (olong != null) {
            if (olong < System.currentTimeMillis()) {
               this.safeLanding.remove(player.getUniqueId());
            } else {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      if (event.hasChangedBlock()) {
         Player player = event.getPlayer();
         UUID uuid = player.getUniqueId();
         PortalRtpModule.Pending portalrtpmodule$pending = this.pending.get(uuid);
         if (portalrtpmodule$pending != null) {
            Location location1 = event.getTo();
            if (location1 != null
               && portalrtpmodule$pending.start.getWorld() == location1.getWorld()
               && portalrtpmodule$pending.start.distanceSquared(location1) > 0.01) {
               this.cancelPending(uuid, true);
            }
         } else {
            Location location = event.getTo();
            if (location != null && this.onTriggerBlock(location)) {
               this.tryOpen(player, location.getBlock().getLocation());
            }
         }
      }
   }

   private void cancelPending(UUID uuid, boolean notify) {
      PortalRtpModule.Pending portalrtpmodule$pending = this.pending.remove(uuid);
      if (portalrtpmodule$pending != null) {
         if (portalrtpmodule$pending.task != null) {
            portalrtpmodule$pending.task.cancel();
         }

         if (notify) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
               this.announce(player, "cancelled");
               this.playSound(player, "error");
            }
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      this.cancelPending(uuid, false);
      this.portalGuiCooldown.remove(uuid);
      this.safeLanding.remove(uuid);
      this.queue.remove(uuid);
      this.queueRequests.remove(uuid);
      this.queueRequests.values().remove(uuid);
      this.cancelQueueWait(uuid);
   }

   private void openDuelQueue(Player player) {
      DuelModule duelmodule = this.plugin.modules().get(DuelModule.class);
      if (duelmodule == null) {
         player.sendMessage(Text.c("&#FF0000&lERROR &8▷ &fDuels are not available right now."));
      } else {
         duelmodule.joinQueuePublic(player);
      }
   }

   private void handleQueueCommand(Player player, String[] args) {
      if (!player.hasPermission("sharded.rtp.use")) {
         this.chat(player, "no-permission");
      } else if (args.length == 0) {
         this.joinQueue(player);
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);
         if (s.equals("leave") || s.equals("cancel")) {
            this.leaveQueue(player);
         } else if (s.equals("accept")) {
            UUID uuid = this.queueRequests.remove(player.getUniqueId());
            Player player1 = uuid == null ? null : Bukkit.getPlayer(uuid);
            if (player1 != null && player1.isOnline()) {
               this.startQueueTeleport(player, player1);
            } else {
               this.chatQueue(player, "no-request");
            }
         } else {
            Player playerx = Bukkit.getPlayerExact(args[0]);
            if (playerx != null && playerx.isOnline()) {
               if (playerx.getUniqueId().equals(player.getUniqueId())) {
                  this.chatQueue(player, "self");
               } else {
                  this.queueRequests.put(playerx.getUniqueId(), player.getUniqueId());
                  this.chatQueue(player, "requested", "%player%", playerx.getName());
                  this.chatQueue(playerx, "incoming", "%player%", player.getName());
               }
            } else {
               this.chatQueue(player, "offline");
            }
         }
      }
   }

   private void joinQueue(Player player) {
      if (!this.canUseRtp(player)) {
         this.chat(player, "wrong-world", "%world%", this.portalWorld());
      } else {
         Iterator<UUID> iterator = this.queue.iterator();

         while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            if (!uuid.equals(player.getUniqueId())) {
               Player playerx = Bukkit.getPlayer(uuid);
               if (playerx != null && playerx.isOnline()) {
                  iterator.remove();
                  this.cancelQueueWait(uuid);
                  this.cancelQueueWait(player.getUniqueId());
                  this.startQueueTeleport(player, playerx);
                  return;
               }

               iterator.remove();
            }
         }

         this.queue.add(player.getUniqueId());
         this.chatQueue(player, "looking");
         this.broadcastQueueJoin(player);
         this.startQueueWait(player);
      }
   }

   private void startQueueWait(Player player) {
      this.cancelQueueWait(player.getUniqueId());
      int i = Math.max(1, this.config.getInt("queue.countdown-seconds", 5));
      int[] aint = new int[]{i};
      BukkitTask bukkittask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
         if (player.isOnline() && this.queue.contains(player.getUniqueId())) {
            String s = this.queueMessage("waiting", "%seconds%", String.valueOf(aint[0]));
            if (s != null) {
               player.sendActionBar(Text.c(s));
            }

            aint[0] = aint[0] <= 1 ? i : aint[0] - 1;
         } else {
            this.cancelQueueWait(player.getUniqueId());
         }
      }, 0L, 20L);
      this.queueWaitTasks.put(player.getUniqueId(), bukkittask);
   }

   private void cancelQueueWait(UUID uuid) {
      BukkitTask bukkittask = this.queueWaitTasks.remove(uuid);
      if (bukkittask != null) {
         bukkittask.cancel();
      }
   }

   private void cancelAllQueueWaitTasks() {
      for (BukkitTask bukkittask : this.queueWaitTasks.values()) {
         if (bukkittask != null) {
            bukkittask.cancel();
         }
      }

      this.queueWaitTasks.clear();
   }

   private void broadcastQueueJoin(Player player) {
      String s = this.queueMessage("broadcast", "%player%", player.getName());
      if (s != null) {
         Component component = Text.c(s);
         String s1 = this.config.getString("queue.broadcast-command", "");
         if (s1 != null && !s1.isBlank()) {
            component = component.clickEvent(ClickEvent.runCommand(s1.startsWith("/") ? s1 : "/" + s1));
            String s2 = this.config.getString("queue.broadcast-hover", "");
            if (s2 != null && !s2.isBlank()) {
               component = component.hoverEvent(HoverEvent.showText(Text.c(Text.apply(s2, "%player%", player.getName()))));
            }
         }

         for (Player playerx : Bukkit.getOnlinePlayers()) {
            if (!playerx.getUniqueId().equals(player.getUniqueId())) {
               playerx.sendMessage(component);
            }
         }
      }
   }

   private void leaveQueue(Player player) {
      boolean flag = this.queue.remove(player.getUniqueId());
      flag |= this.queueRequests.remove(player.getUniqueId()) != null;
      this.cancelQueueWait(player.getUniqueId());
      this.chatQueue(player, flag ? "cancelled" : "not-in");
   }

   private void startQueueTeleport(Player first, Player second) {
      if (this.canUseRtp(first) && this.canUseRtp(second)) {
         this.cancelQueueWait(first.getUniqueId());
         this.cancelQueueWait(second.getUniqueId());
         PortalRtpModule.Destination portalrtpmodule$destination = this.defaultDestination();
         if (portalrtpmodule$destination != null && this.isUnlocked(portalrtpmodule$destination)) {
            int i = first.getStatistic(Statistic.PLAYER_KILLS);
            int j = second.getStatistic(Statistic.PLAYER_KILLS);
            String s = this.queueMessage(
               "score", "%player%", first.getName(), "%score%", String.valueOf(i), "%other%", second.getName(), "%other_score%", String.valueOf(j)
            );
            if (s != null) {
               first.sendMessage(Text.c(s));
               second.sendMessage(Text.c(s));
            }

            this.openQueueMatchGui(first, second, i, j);
            this.openQueueMatchGui(second, first, j, i);
            int k = this.config.getInt("queue.countdown-seconds", 5);
            String s1 = this.queueMessage("found", "%seconds%", String.valueOf(k));
            if (s1 != null) {
               first.sendMessage(Text.c(s1));
               second.sendMessage(Text.c(s1));
            }

            double d0 = this.config.getDouble("queue.offset", 8.0);
            this.beginTeleport(first, portalrtpmodule$destination, k, () -> {
               if (second.isOnline()) {
                  Location location = this.nearbyLanding(first.getLocation(), d0);
                  second.teleportAsync(location, TeleportCause.PLUGIN)
                     .thenAccept(success -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                           if (second.isOnline() && Boolean.TRUE.equals(success)) {
                              this.applySafeLanding(second);
                              this.announce(second, "teleported");
                              this.playSound(second, "teleport");
                           }
                        }));
               }
            });
         } else {
            this.chat(first, "restricted");
            this.chat(second, "restricted");
         }
      } else {
         this.chat(first, "wrong-world", "%world%", this.portalWorld());
         this.chat(second, "wrong-world", "%world%", this.portalWorld());
      }
   }

   private void openQueueMatchGui(Player viewer, Player other, int viewerScore, int otherScore) {
      Inventory inventory = Bukkit.createInventory(null, 27, Text.c(this.config.getString("queue.match-title", "&8RTP Match")));
      ItemStack itemstack = this.item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());

      for (int i = 0; i < 27; i++) {
         inventory.setItem(i, itemstack);
      }

      inventory.setItem(11, this.playerHead(viewer, viewerScore, true));
      inventory.setItem(15, this.playerHead(other, otherScore, false));
      viewer.openInventory(inventory);
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         if (viewer.isOnline() && viewer.getOpenInventory().getTopInventory().equals(inventory)) {
            viewer.closeInventory();
         }
      }, 40L);
   }

   private ItemStack playerHead(Player player, int score, boolean self) {
      ItemStack itemstack = new ItemStack(Material.PLAYER_HEAD);
      if (itemstack.getItemMeta() instanceof SkullMeta skullmeta) {
         skullmeta.setOwningPlayer(player);
         skullmeta.displayName(Text.c((self ? "&#94FF00" : "&#00C1FF") + "&l" + player.getName()));
         skullmeta.lore(
            List.of(
               Text.c("&8RTP Queue"), Text.c(""), Text.c("&#FFBA00Score: &#FFBA00&l" + score + " &fkills"), Text.c(""), Text.c(self ? "&7You" : "&7Teammate")
            )
         );
         itemstack.setItemMeta(skullmeta);
      }

      return itemstack;
   }

   private Location nearbyLanding(Location base, double offset) {
      World world = base.getWorld();
      if (world == null) {
         return base;
      } else {
         int i = base.getBlockX() + (int)Math.round(offset);
         int j = base.getBlockZ();
         Block block = world.getHighestBlockAt(i, j, HeightMap.MOTION_BLOCKING_NO_LEAVES);
         Location location = block.getLocation().add(0.5, 1.0, 0.5);
         location.setYaw(base.getYaw());
         location.setPitch(base.getPitch());
         return location;
      }
   }

   private Location pollPool(PortalRtpModule.Destination dest) {
      Deque<Location> deque = this.pools.get(dest.id);

      while (deque != null && !deque.isEmpty()) {
         Location location = deque.poll();
         if (location != null && location.getWorld() != null) {
            return location.clone();
         }
      }

      return null;
   }

   private void refillPools() {
      int i = Math.max(1, this.config.getInt("pool-size", 8));
      int j = Math.max(1, this.config.getInt("concurrent-searches", 1));
      if (this.searches.size() < j) {
         for (PortalRtpModule.Destination portalrtpmodule$destination : this.destinations.values()) {
            if (this.isUnlocked(portalrtpmodule$destination)) {
               World world = Bukkit.getWorld(portalrtpmodule$destination.worldName);
               if (world != null) {
                  Deque<Location> deque = this.pools.computeIfAbsent(portalrtpmodule$destination.id, key -> new ConcurrentLinkedDeque<>());
                  if (deque.size() < i) {
                     this.request(portalrtpmodule$destination, world, found -> {
                        if (found != null) {
                           this.pools.computeIfAbsent(portalrtpmodule$destination.id, key -> new ConcurrentLinkedDeque<>()).add(found);
                           this.refillRequested = true;
                        }
                     });
                     if (this.searches.size() >= j) {
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   private void request(PortalRtpModule.Destination dest, World world, Consumer<Location> callback) {
      PortalRtpModule.Search portalrtpmodule$search = new PortalRtpModule.Search(dest, world, callback);
      portalrtpmodule$search.attemptsLeft = Math.max(1, this.config.getInt("attempts", 30));
      portalrtpmodule$search.generatedOnlyLeft = this.config.getBoolean("prefer-generated", true)
         ? Math.max(0, this.config.getInt("generated-attempts", 20))
         : 0;
      this.searches.add(portalrtpmodule$search);
   }

   private Location searchBlocking(PortalRtpModule.Destination dest, World world) {
      for (int i = 0; i < Math.max(1, this.config.getInt("attempts", 30)); i++) {
         int[] aint = this.randomPoint(dest);
         if (world.isChunkGenerated(aint[0] >> 4, aint[1] >> 4)) {
            Location location = this.evaluate(dest, world, aint[0], aint[1]);
            if (location != null) {
               return location;
            }
         }
      }

      return null;
   }

   private void tickSearches() {
      if (!this.searches.isEmpty()) {
         this.refillRequested = false;
         int i = Math.max(1, this.config.getInt("main-checks-per-tick", 1));
         Iterator<PortalRtpModule.Search> iterator = this.searches.iterator();

         while (iterator.hasNext()) {
            PortalRtpModule.Search portalrtpmodule$search = iterator.next();
            if (portalrtpmodule$search.pendingChunk == null) {
               if (!this.startAttempt(portalrtpmodule$search)) {
                  iterator.remove();
                  portalrtpmodule$search.finish(null);
               }
            } else if (portalrtpmodule$search.pendingChunk.isDone() && i > 0) {
               i--;
               Chunk chunk = null;

               try {
                  chunk = portalrtpmodule$search.pendingChunk.getNow(null);
               } catch (Throwable throwable) {
               }

               portalrtpmodule$search.pendingChunk = null;
               Location location = chunk == null
                  ? null
                  : this.evaluate(portalrtpmodule$search.dest, portalrtpmodule$search.world, portalrtpmodule$search.x, portalrtpmodule$search.z);
               if (location != null) {
                  iterator.remove();
                  portalrtpmodule$search.finish(location);
               }
            }
         }

         if (this.refillRequested) {
            this.refillRequested = false;
            this.refillPools();
         }
      }
   }

   private boolean startAttempt(PortalRtpModule.Search search) {
      while (search.attemptsLeft > 0) {
         search.attemptsLeft--;
         int[] aint = this.randomPoint(search.dest);
         int i = aint[0] >> 4;
         int j = aint[1] >> 4;
         boolean flag = search.generatedOnlyLeft > 0;
         if (flag) {
            search.generatedOnlyLeft--;
         }

         boolean flag1 = search.world.isChunkGenerated(i, j);
         if (flag1 || !flag && this.claimGenerateBudget()) {
            search.x = aint[0];
            search.z = aint[1];
            search.pendingChunk = search.world.getChunkAtAsync(i, j, true);
            return true;
         }
      }

      return false;
   }

   private int[] randomPoint(PortalRtpModule.Destination dest) {
      ThreadLocalRandom threadlocalrandom = ThreadLocalRandom.current();
      int i = Math.max(16, dest.radius);
      return new int[]{dest.centerX + threadlocalrandom.nextInt(-i, i + 1), dest.centerZ + threadlocalrandom.nextInt(-i, i + 1)};
   }

   private boolean claimGenerateBudget() {
      int i = this.config.getInt("generate-per-minute", 30);
      if (i <= 0) {
         return false;
      } else {
         long j = System.currentTimeMillis();
         if (j - this.generateWindowStart > 60000L) {
            this.generateWindowStart = j;
            this.generateUsed = 0;
         }

         if (this.generateUsed >= i) {
            return false;
         } else {
            this.generateUsed++;
            return true;
         }
      }
   }

   private Location evaluate(PortalRtpModule.Destination dest, World world, int x, int z) {
      int i = Math.max(world.getMinHeight() + 1, dest.minY);
      Block block = null;
      if (dest.maxY > 0) {
         int j = Math.min(dest.maxY, world.getMaxHeight() - 3);

         for (int k = j; k >= i; k--) {
            Block block1 = world.getBlockAt(x, k, z);
            if (block1.getType().isSolid()) {
               if (this.hasHeadroom(world, x, k, z)) {
                  block = block1;
               }
               break;
            }
         }

         if (block == null) {
            for (int l = j - 1; l >= i; l--) {
               Block block3 = world.getBlockAt(x, l, z);
               if (block3.getType().isSolid() && this.hasHeadroom(world, x, l, z)) {
                  block = block3;
                  break;
               }
            }
         }
      } else {
         Block block2 = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
         if (block2.getY() >= i && this.hasHeadroom(world, x, block2.getY(), z)) {
            block = block2;
         }
      }

      if (block == null || !block.getType().isSolid()) {
         return null;
      } else if (this.isBlockedBlock(block.getType())) {
         return null;
      } else if (this.isBlockedBiome(world, x, block.getY(), z)) {
         return null;
      } else {
         Location location = block.getLocation().add(0.5, 1.0, 0.5);
         location.setYaw((float)ThreadLocalRandom.current().nextInt(360));
         return location;
      }
   }

   private boolean hasHeadroom(World world, int x, int y, int z) {
      Block block = world.getBlockAt(x, y + 1, z);
      Block block1 = world.getBlockAt(x, y + 2, z);
      return block.isPassable() && block1.isPassable() && !block.isLiquid() && !block1.isLiquid();
   }

   private boolean isBlockedBlock(Material material) {
      String s = material.name();

      for (String s1 : this.config.getStringList("blocked-blocks")) {
         if (s1 != null && !s1.isBlank() && s.contains(s1.toUpperCase(Locale.ROOT))) {
            return true;
         }
      }

      return false;
   }

   private boolean isBlockedBiome(World world, int x, int y, int z) {
      List<String> list = this.config.getStringList("blocked-biomes");
      if (list.isEmpty()) {
         return false;
      } else {
         String s;
         try {
            s = world.getBiome(x, y, z).getKey().getKey().toUpperCase(Locale.ROOT);
         } catch (Throwable throwable) {
            return false;
         }

         for (String s1 : list) {
            if (s1 != null && s1.trim().toUpperCase(Locale.ROOT).equals(s)) {
               return true;
            }
         }

         return false;
      }
   }

   private double cost() {
      Object object = this.config.get("cost");
      if (object instanceof Number number) {
         return number.doubleValue();
      } else {
         return object instanceof String s ? parseAmount(s) : 0.0;
      }
   }

   private static double parseAmount(String raw) {
      String s = raw.trim().toLowerCase(Locale.ROOT).replace(",", "");
      if (s.isEmpty()) {
         return 0.0;
      } else {
         double d0 = 1.0;
         char c0 = s.charAt(s.length() - 1);
         if (c0 == 'k') {
            d0 = 1000.0;
         } else if (c0 == 'm') {
            d0 = 1000000.0;
         } else if (c0 == 'b') {
            d0 = 1.0E9;
         }

         if (d0 > 1.0) {
            s = s.substring(0, s.length() - 1);
         }

         try {
            return Double.parseDouble(s) * d0;
         } catch (NumberFormatException numberformatexception) {
            return 0.0;
         }
      }
   }

   private String message(String key, String... replacements) {
      return this.lookup("messages.", "queue.", key, replacements);
   }

   private String queueMessage(String key, String... replacements) {
      return this.lookup("queue.", "messages.", key, replacements);
   }

   private String lookup(String first, String second, String key, String... replacements) {
      String s = this.config.getString(first + key);
      if (s == null || s.isBlank()) {
         s = this.config.getString(second + key);
      }

      return s != null && !s.isBlank() ? Text.apply(s, replacements) : null;
   }

   private void chat(CommandSender sender, String key, String... replacements) {
      String s = this.message(key, replacements);
      if (s != null) {
         sender.sendMessage(Text.c(s));
      }
   }

   private void chatQueue(CommandSender sender, String key, String... replacements) {
      String s = this.queueMessage(key, replacements);
      if (s != null) {
         sender.sendMessage(Text.c(s));
      }
   }

   private void announce(Player player, String key, String... replacements) {
      String s = this.message(key, replacements);
      if (s != null) {
         if (this.config.getBoolean("messages.actionbar", true)) {
            player.sendActionBar(Text.c(s));
         } else {
            player.sendMessage(Text.c(s));
         }
      }
   }

   private void playSound(Player player, String key) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("sounds." + key);
      if (configurationsection != null && configurationsection.getBoolean("enabled", true)) {
         String s = configurationsection.getString("sound");
         if (s != null && !s.isBlank()) {
            player.playSound(player.getLocation(), s, (float)configurationsection.getDouble("volume", 1.0), (float)configurationsection.getDouble("pitch", 1.0));
         }
      }
   }

   private static final class Destination {
      private final String id;
      private final String display;
      private final String worldName;
      private final boolean lockedByDefault;
      private final int slot;
      private final Material material;
      private final String name;
      private final List<String> lore;
      private final int radius;
      private final int centerX;
      private final int centerZ;
      private final int minY;
      private final int maxY;

      private Destination(String id, ConfigurationSection section) {
         this.id = id;
         this.display = section.getString("display", Text.pretty(id));
         this.worldName = section.getString("world", "world");
         this.lockedByDefault = section.getBoolean("locked", false);
         this.slot = section.getInt("slot", -1);
         Material material = Material.matchMaterial(section.getString("material", "GRASS_BLOCK").toUpperCase(Locale.ROOT));
         this.material = material == null ? Material.GRASS_BLOCK : material;
         this.name = section.getString("name", this.display);
         this.lore = section.getStringList("lore");
         this.radius = Math.max(16, section.getInt("radius", 5000));
         this.centerX = section.getInt("center-x", 0);
         this.centerZ = section.getInt("center-z", 0);
         this.minY = section.getInt("min-y", 0);
         this.maxY = section.getInt("max-y", 0);
      }
   }

   private static final class Economy {
      private static final Map<String, Object> CACHE = new HashMap<>();

      private static Object provider() {
         return Bukkit.getPluginManager().getPlugin("Vault") == null ? null : CACHE.computeIfAbsent("economy", key -> {
            try {
               Class<?> oclass = Class.forName("net.milkbowl.vault.economy.Economy");
               Object object = Bukkit.getServicesManager().getRegistration(oclass);
               return object == null ? null : object.getClass().getMethod("getProvider").invoke(object);
            } catch (Throwable throwable) {
               return null;
            }
         });
      }

      private static boolean has(Player player, double amount) {
         Object object = provider();
         if (object == null) {
            return true;
         } else {
            try {
               Object object1 = object.getClass().getMethod("has", OfflinePlayer.class, double.class).invoke(object, player, amount);
               return Boolean.TRUE.equals(object1);
            } catch (Throwable throwable) {
               return true;
            }
         }
      }

      private static boolean withdraw(Player player, double amount) {
         Object object = provider();
         if (object == null) {
            return true;
         } else {
            try {
               object.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class).invoke(object, player, amount);
               return true;
            } catch (Throwable throwable) {
               return true;
            }
         }
      }
   }

   private static final class MenuHolder implements InventoryHolder {
      private Inventory inventory;

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static final class Pending {
      private final Location start;
      private final PortalRtpModule.Destination dest;
      private final Runnable afterTeleport;
      private BukkitTask task;
      private volatile Location target;
      private volatile boolean searchFinished;

      private Pending(Location start, PortalRtpModule.Destination dest, Runnable afterTeleport) {
         this.start = start;
         this.dest = dest;
         this.afterTeleport = afterTeleport;
      }
   }

   private static final class Search {
      private final PortalRtpModule.Destination dest;
      private final World world;
      private final Consumer<Location> callback;
      private CompletableFuture<Chunk> pendingChunk;
      private int attemptsLeft;
      private int generatedOnlyLeft;
      private int x;
      private int z;

      private Search(PortalRtpModule.Destination dest, World world, Consumer<Location> callback) {
         this.dest = dest;
         this.world = world;
         this.callback = callback;
      }

      private void finish(Location location) {
         if (this.callback != null) {
            this.callback.accept(location);
         }
      }
   }
}
