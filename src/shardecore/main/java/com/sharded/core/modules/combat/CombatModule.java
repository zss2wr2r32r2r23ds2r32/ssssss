package com.sharded.core.modules.combat;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.coreprotect.CoreProtectModule;
import com.sharded.core.modules.koth.KothModule;
import com.sharded.core.modules.outpost.OutpostModule;
import com.sharded.core.util.CombatWallTracker;
import com.sharded.core.util.CuboidRegion;
import com.sharded.core.util.RegionSetup;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.Vector;

public final class CombatModule extends Module implements CommandExecutor, TabCompleter {
   private final RegionSetup setup = new RegionSetup();
   private CuboidRegion region;
   private final Map<UUID, Long> taggedUntil = new HashMap<>();
   private final CombatWallTracker wallTracker = new CombatWallTracker();
   private final Set<UUID> wasTagged = new HashSet<>();
   private int tickTask = -1;

   public CombatModule(ShardedCore plugin) {
      super(plugin, "combat");
   }

   @Override
   protected void onEnable() {
      this.reloadRegion();
      this.registerCommand("combat", this);
      this.tickTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, this::tick, 5L, 5L);
   }

   @Override
   protected void onDisable() {
      if (this.tickTask >= 0) {
         Bukkit.getScheduler().cancelTask(this.tickTask);
      }

      for (Player player : Bukkit.getOnlinePlayers()) {
         this.wallTracker.clear(player);
      }
   }

   public boolean isTagged(Player player) {
      Long olong = this.taggedUntil.get(player.getUniqueId());
      return olong != null && System.currentTimeMillis() < olong;
   }

   private int tagSeconds() {
      return this.config.getInt("tag-seconds", 15);
   }

   private void tag(Player player) {
      this.taggedUntil.put(player.getUniqueId(), System.currentTimeMillis() + (long)this.tagSeconds() * 1000L);
   }

   private void reloadRegion() {
      this.region = CuboidRegion.fromSection(this.config.getConfigurationSection("region"));
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.combat.admin")) {
            this.send(sender, "no-permission", new String[0]);
            return true;
         } else {
            String s = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
            if (s.equals("pos1")) {
               this.setup.setPos1(player, player.getLocation());
               this.send(player, "pos1-set", new String[0]);
               return true;
            } else if (s.equals("pos2")) {
               this.setup.setPos2(player, player.getLocation());
               this.send(player, "pos2-set", new String[0]);
               return true;
            } else if (s.equals("setregion")) {
               CuboidRegion cuboidregion = this.setup.build(player);
               if (cuboidregion == null) {
                  this.send(player, "need-positions", new String[0]);
                  return true;
               } else {
                  this.region = cuboidregion;
                  cuboidregion.write(this.config.createSection("region"));
                  this.saveConfig();
                  this.send(player, "region-set", new String[0]);
                  return true;
               }
            } else {
               this.send(player, "usage", new String[0]);
               return true;
            }
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Player player) {
         Player player1 = null;
         Entity entity = event.getDamager();
         if (entity instanceof Player player2) {
            player1 = player2;
         } else if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player3) {
            player1 = player3;
         }

         if (player1 != null) {
            this.tag(player1);
            this.tag(player);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      UUID uuid = player.getUniqueId();
      this.taggedUntil.remove(uuid);
      this.wasTagged.remove(uuid);
      this.wallTracker.clear(player);
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      this.wallTracker.clear(player);
      boolean flag = this.isTagged(player);
      this.taggedUntil.remove(player.getUniqueId());
      this.wasTagged.remove(player.getUniqueId());
      if (flag) {
         if (this.config.getBoolean("kill-on-logout", true)) {
            player.setHealth(0.0);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      Player player = event.getPlayer();
      if (this.isTagged(player)) {
         if (!CombatRules.bypassesCombatLock(player)) {
            event.setCancelled(true);
            this.send(player, "no-commands", new String[0]);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onTeleport(PlayerTeleportEvent event) {
      Player player = event.getPlayer();
      if (this.isTagged(player) && !CombatRules.bypassesCombatLock(player)) {
         if (CombatRules.blocksTaggedSpawnTeleport(event.getCause())) {
            CuboidRegion cuboidregion = this.combatRegion();
            Location location = event.getTo();
            if (cuboidregion != null && location != null && location.getWorld() != null) {
               if (cuboidregion.world().equals(location.getWorld().getName())) {
                  if (cuboidregion.contains(location)) {
                     event.setCancelled(true);
                     Location location1 = this.nearestOutside(cuboidregion, event.getFrom());
                     Bukkit.getScheduler().runTask(this.plugin, () -> this.pushBack(player, location1, cuboidregion));
                     if (event.getCause() == TeleportCause.ENDER_PEARL) {
                        this.send(player, "no-pearl-spawn", new String[0]);
                     }
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
   public void onMove(PlayerMoveEvent event) {
      if (this.taggedUntil.isEmpty() || !event.hasChangedBlock()) {
         return;
      }
      if (event.hasChangedBlock()) {
         if (this.isTagged(event.getPlayer()) && !CombatRules.bypassesCombatLock(event.getPlayer())) {
            CuboidRegion cuboidregion = this.combatRegion();
            if (cuboidregion != null) {
               Location location = event.getTo();
               Location location1 = event.getFrom();
               if (location != null) {
                  if (cuboidregion.world().equals(location.getWorld().getName())) {
                     if (cuboidregion.contains(location)) {
                        event.setCancelled(true);
                        Location location2 = cuboidregion.contains(location1) ? this.nearestOutside(cuboidregion, location1) : location1.clone();
                        event.setTo(location2);
                        this.pushBack(event.getPlayer(), location2, cuboidregion);
                     }
                  }
               }
            }
         }
      }
   }

   private CuboidRegion combatRegion() {
      if (this.region != null) {
         return this.region;
      } else {
         CoreProtectModule coreprotectmodule = this.plugin.modules().get(CoreProtectModule.class);
         if (coreprotectmodule != null) {
            CuboidRegion cuboidregion = coreprotectmodule.region("combat");
            return cuboidregion != null ? cuboidregion : coreprotectmodule.region("spawn");
         } else {
            return null;
         }
      }
   }

   private Location nearestOutside(CuboidRegion spawn, Location loc) {
      int i = loc.getBlockX();
      int j = loc.getBlockY();
      int k = loc.getBlockZ();
      int l = i;
      int i1 = k;
      if (!spawn.contains(loc)) {
         return loc.clone();
      } else {
         int j1 = i - spawn.minX();
         int k1 = spawn.maxX() - i;
         int l1 = k - spawn.minZ();
         int i2 = spawn.maxZ() - k;
         int j2 = Math.min(Math.min(j1, k1), Math.min(l1, i2));
         if (j2 == j1) {
            l = spawn.minX() - 1;
         } else if (j2 == k1) {
            l = spawn.maxX() + 1;
         } else {
            i1 = j2 == l1 ? spawn.minZ() - 1 : spawn.maxZ() + 1;
         }

         return new Location(loc.getWorld(), (double)l + 0.5, (double)j, (double)i1 + 0.5, loc.getYaw(), loc.getPitch());
      }
   }

   private void pushBack(Player player, Location safe, CuboidRegion spawn) {
      if (player.isOnline()) {
         player.teleport(safe);
         int i = (spawn.minX() + spawn.maxX()) / 2;
         int j = (spawn.minZ() + spawn.maxZ()) / 2;
         Vector vector = safe.toVector().subtract(new Vector((double)i, safe.getY(), (double)j));
         if (vector.lengthSquared() < 0.01) {
            vector = new Vector(0, 0, 1);
         }

         vector.normalize().multiply(this.config.getDouble("pushback-strength", 1.5));
         vector.setY(this.config.getDouble("pushback-y", 0.35));
         player.setVelocity(vector);
         this.send(player, "pushback", new String[0]);
         if (this.config.getBoolean("red-glass-walls", true)) {
            this.wallTracker.showLocalSpawnWall(player, spawn, safe);
         }
      }
   }

   private void tick() {
      long i = System.currentTimeMillis();
      if (this.taggedUntil.isEmpty() && this.wasTagged.isEmpty()) {
         return;
      }
      Iterator<Entry<UUID, Long>> iterator = this.taggedUntil.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, Long> entry = iterator.next();
         if (entry.getValue() <= i) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
               this.wallTracker.clear(player);
            }

            iterator.remove();
         }
      }

      KothModule kothmodule = this.plugin.modules().get(KothModule.class);
      OutpostModule outpostmodule = this.plugin.modules().get(OutpostModule.class);

      for (Player player1 : Bukkit.getOnlinePlayers()) {
         UUID uuid = player1.getUniqueId();
         Long olong = this.taggedUntil.get(uuid);
         boolean flag = olong != null && olong > i;
         if (flag) {
            this.wasTagged.add(uuid);
            CuboidRegion cuboidregion = this.combatRegion();
            if (cuboidregion != null && cuboidregion.contains(player1.getLocation()) && !CombatRules.bypassesCombatLock(player1)) {
               Location location = this.nearestOutside(cuboidregion, player1.getLocation());
               player1.teleport(location);
               this.pushBack(player1, location, cuboidregion);
            }

            if (!this.eventActionBarActive(player1, kothmodule, outpostmodule)) {
               long j = (olong - i) / 1000L;
               String s = this.config
                  .getString("actionbar", "&#FF0000&lCOMBAT &8▷ &rYou are in combat for &#FF0000&n%seconds%&r&#FF0000s")
                  .replace("%seconds%", String.valueOf(j));
               player1.sendActionBar(Text.c(s));
               if (this.config.getBoolean("red-glass-walls", true)
                  && cuboidregion != null
                  && cuboidregion.world().equals(player1.getWorld().getName())
                  && this.nearSpawnBorder(player1.getLocation(), cuboidregion, 10)) {
                  this.wallTracker.showLocalSpawnWall(player1, cuboidregion, player1.getLocation());
               }
            }
         } else if (this.wasTagged.remove(uuid)) {
            this.wallTracker.clear(player1);
         }
      }
   }

   private boolean eventActionBarActive(Player player, KothModule koth, OutpostModule outpost) {
      return koth != null && koth.isActive() && koth.isInside(player) ? true : outpost != null && outpost.isActive() && outpost.isInside(player);
   }

   private boolean nearSpawnBorder(Location loc, CuboidRegion spawn, int margin) {
      int i = loc.getBlockX();
      int j = loc.getBlockZ();
      return i >= spawn.minX() - margin && i <= spawn.maxX() + margin && j >= spawn.minZ() - margin && j <= spawn.maxZ() + margin;
   }

   private void saveConfig() {
      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (Exception exception) {
         this.plugin.getLogger().warning("[combat] Could not save config: " + exception.getMessage());
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission("sharded.combat.admin")) {
         return List.of();
      } else {
         return args.length == 1 ? TabCompleteHelper.filter(args[0], "pos1", "pos2", "setregion") : List.of();
      }
   }
}
