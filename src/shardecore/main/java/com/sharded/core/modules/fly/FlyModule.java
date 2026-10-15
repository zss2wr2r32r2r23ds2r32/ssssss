package com.sharded.core.modules.fly;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class FlyModule extends Module implements CommandExecutor, TabCompleter {
   private final Set<UUID> flying = new HashSet<>();
   private final Set<UUID> noFallDamage = new HashSet<>();
   private final Map<UUID, Location> pos1 = new HashMap<>();
   private final Map<UUID, Location> pos2 = new HashMap<>();

   public FlyModule(ShardedCore plugin) {
      super(plugin, "fly");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("fly", this);
   }

   @Override
   protected void onDisable() {
      for (UUID uuid : Set.copyOf(this.flying)) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null) {
            this.disableFlight(player, true);
         }
      }

      this.flying.clear();
      this.noFallDamage.clear();
      this.pos1.clear();
      this.pos2.clear();
   }

   private boolean regionSet() {
      return this.config.getBoolean("region.set", false);
   }

   private String flyWorld() {
      return this.config.getString("world", "spawn");
   }

   private boolean inRegion(Location location) {
      if (!this.regionSet()) {
         return false;
      } else if (location.getWorld() != null && location.getWorld().getName().equalsIgnoreCase(this.flyWorld())) {
         double d0 = Math.min(this.config.getDouble("region.x1"), this.config.getDouble("region.x2"));
         double d1 = Math.max(this.config.getDouble("region.x1"), this.config.getDouble("region.x2"));
         double d2 = Math.min(this.config.getDouble("region.y1"), this.config.getDouble("region.y2"));
         double d3 = Math.max(this.config.getDouble("region.y1"), this.config.getDouble("region.y2"));
         double d4 = Math.min(this.config.getDouble("region.z1"), this.config.getDouble("region.z2"));
         double d5 = Math.max(this.config.getDouble("region.z1"), this.config.getDouble("region.z2"));
         return location.getX() >= d0
            && location.getX() <= d1
            && location.getY() >= d2
            && location.getY() <= d3
            && location.getZ() >= d4
            && location.getZ() <= d5;
      } else {
         return false;
      }
   }

   private boolean canFlyHere(Player player) {
      if (!this.regionSet()) {
         return false;
      } else {
         return player.hasPermission("sharded.fly.anywhere") ? true : this.inRegion(player.getLocation());
      }
   }

   private void saveRegion(Location a, Location b) {
      this.config.set("region.set", true);
      this.config.set("region.x1", a.getX());
      this.config.set("region.y1", a.getY());
      this.config.set("region.z1", a.getZ());
      this.config.set("region.x2", b.getX());
      this.config.set("region.y2", b.getY());
      this.config.set("region.z2", b.getZ());

      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (IOException ioexception) {
         this.plugin.getLogger().warning("Could not save fly region: " + ioexception.getMessage());
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player player)) {
         this.send(sender, "players-only", new String[0]);
         return true;
      } else if (args.length == 0) {
         this.toggleSelf(player);
         return true;
      } else {
         String s = args[0].toLowerCase();
         switch (s) {
            case "speed":
               if (!player.hasPermission("sharded.fly.speed")) {
                  this.send(player, "no-permission", new String[0]);
                  return true;
               }

               if (args.length < 2) {
                  this.send(player, "speed-usage", new String[0]);
                  return true;
               }

               int i;
               try {
                  i = Integer.parseInt(args[1]);
               } catch (NumberFormatException numberformatexception) {
                  this.send(player, "speed-usage", new String[0]);
                  return true;
               }

               i = Math.max(1, Math.min(10, i));
               Player player2 = player;
               if (args.length >= 3) {
                  if (!player.hasPermission("sharded.fly.others")) {
                     this.send(player, "no-permission", new String[0]);
                     return true;
                  }

                  player2 = Bukkit.getPlayerExact(args[2]);
                  if (player2 == null) {
                     this.send(player, "player-not-found", new String[]{"%player%", args[2]});
                     return true;
                  }
               }

               player2.setFlySpeed((float)i / 10.0F);
               this.send(player, "speed-set", new String[]{"%speed%", String.valueOf(i), "%player%", player2.getName()});
               if (player2 != player) {
                  this.send(player2, "speed-set-by-other", new String[]{"%speed%", String.valueOf(i)});
               }
               break;
            case "pos1":
               if (!player.hasPermission("sharded.fly.admin")) {
                  this.send(player, "no-permission", new String[0]);
                  return true;
               }

               this.pos1.put(player.getUniqueId(), player.getLocation());
               this.send(player, "pos-set", new String[]{"%pos%", "1"});
               break;
            case "pos2":
               if (!player.hasPermission("sharded.fly.admin")) {
                  this.send(player, "no-permission", new String[0]);
                  return true;
               }

               this.pos2.put(player.getUniqueId(), player.getLocation());
               this.send(player, "pos-set", new String[]{"%pos%", "2"});
               break;
            case "setregion":
               if (!player.hasPermission("sharded.fly.admin")) {
                  this.send(player, "no-permission", new String[0]);
                  return true;
               }

               Location location1 = this.pos1.get(player.getUniqueId());
               Location location = this.pos2.get(player.getUniqueId());
               if (location1 == null || location == null) {
                  this.send(player, "region-need-positions", new String[0]);
                  return true;
               }

               if (location1.getWorld() == null
                  || !location1.getWorld().getName().equalsIgnoreCase(this.flyWorld())
                  || !location.getWorld().equals(location1.getWorld())) {
                  this.send(player, "region-wrong-world", new String[]{"%world%", this.flyWorld()});
                  return true;
               }

               this.saveRegion(location1, location);
               this.send(player, "region-saved", new String[]{"%world%", this.flyWorld()});
               break;
            default:
               if (!player.hasPermission("sharded.fly.others")) {
                  this.send(player, "no-permission", new String[0]);
                  return true;
               }

               Player player1 = Bukkit.getPlayerExact(args[0]);
               if (player1 == null) {
                  this.send(player, "player-not-found", new String[]{"%player%", args[0]});
                  return true;
               }

               if (!this.flying.contains(player1.getUniqueId()) && !player1.getAllowFlight()) {
                  if (!this.regionSet()) {
                     this.send(player, "region-not-set", new String[0]);
                     return true;
                  }

                  this.enableFlight(player1);
                  this.send(player, "enabled-other", new String[]{"%player%", player1.getName()});
               } else {
                  this.disableFlight(player1, false);
                  this.send(player, "disabled-other", new String[]{"%player%", player1.getName()});
               }
         }

         return true;
      }
   }

   private void toggleSelf(Player player) {
      if (!player.hasPermission("sharded.fly.use")) {
         this.send(player, "no-permission", new String[0]);
      } else if (this.flying.contains(player.getUniqueId())) {
         this.disableFlight(player, false);
         this.send(player, "disabled", new String[0]);
      } else if (!this.regionSet()) {
         this.send(player, "region-not-set", new String[0]);
      } else if (!player.getWorld().getName().equalsIgnoreCase(this.flyWorld()) && !player.hasPermission("sharded.fly.anywhere")) {
         this.send(player, "wrong-world", new String[]{"%world%", this.flyWorld()});
      } else if (!this.canFlyHere(player)) {
         this.send(player, "not-in-region", new String[0]);
      } else {
         this.enableFlight(player);
         this.send(player, "enabled", new String[0]);
      }
   }

   private void enableFlight(Player player) {
      this.flying.add(player.getUniqueId());
      player.setAllowFlight(true);
      player.setFlying(true);
   }

   private void disableFlight(Player player, boolean silent) {
      this.flying.remove(player.getUniqueId());
      if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
         if (player.isFlying() || player.getAllowFlight()) {
            this.noFallDamage.add(player.getUniqueId());
         }

         player.setFlying(false);
         player.setAllowFlight(false);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      if (event.hasChangedBlock()) {
         Player player = event.getPlayer();
         if (this.flying.contains(player.getUniqueId())) {
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
               if (!player.hasPermission("sharded.fly.anywhere")) {
                  Location location = event.getTo();
                  boolean flag = location.getWorld() != null && location.getWorld().getName().equalsIgnoreCase(this.flyWorld()) && this.inRegion(location);
                  if (!flag) {
                     this.disableFlight(player, false);
                     this.send(player, "left-region", new String[0]);
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onFall(EntityDamageEvent event) {
      if (event.getCause() == DamageCause.FALL) {
         if (event.getEntity() instanceof Player player) {
            if (this.noFallDamage.remove(player.getUniqueId())) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.flying.remove(event.getPlayer().getUniqueId());
      this.noFallDamage.remove(event.getPlayer().getUniqueId());
      this.pos1.remove(event.getPlayer().getUniqueId());
      this.pos2.remove(event.getPlayer().getUniqueId());
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1) {
         List<String> list = new ArrayList<>(List.of("speed"));
         if (sender.hasPermission("sharded.fly.admin")) {
            list.addAll(List.of("pos1", "pos2", "setregion"));
         }

         if (sender.hasPermission("sharded.fly.others")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
               list.add(player.getName());
            }
         }

         list.removeIf(o -> !o.toLowerCase().startsWith(args[0].toLowerCase()));
         return list;
      } else {
         return args.length == 2 && args[0].equalsIgnoreCase("speed") ? List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10") : List.of();
      }
   }
}
