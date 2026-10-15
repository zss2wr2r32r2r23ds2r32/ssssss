package com.sharded.core.modules.protect;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.CuboidRegion;
import com.sharded.core.util.RegionSetup;
import com.sharded.core.util.TabCompleteHelper;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;

public final class ProtectModule extends Module implements CommandExecutor, TabCompleter {
   private static final List<String> SIDE_KEYS = List.of("side1", "side2", "side3", "side4");
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

   public ProtectModule(ShardedCore plugin) {
      super(plugin, "protect");
   }

   @Override
   protected void onEnable() {
      this.reloadRegions();
      this.registerCommand("protect", this);
   }

   private void reloadRegions() {
      this.regions.clear();
      Set<String> set = new HashSet<>();

      for (String s : List.of("spawn", "pvp", "side1", "side2", "side3", "side4")) {
         CuboidRegion cuboidregion = CuboidRegion.fromSection(this.config.getConfigurationSection("regions." + s));
         if (cuboidregion != null) {
            this.regions.put(s, cuboidregion);
            if (SIDE_KEYS.contains(s)) {
               set.add(cuboidregion.world().toLowerCase(Locale.ROOT));
            }
         }
      }

      this.sideWorlds = Set.copyOf(set);
      List<String> list = this.config.getStringList("horn-block-worlds");
      this.hornBlockWorlds = (Set<String>)(list.isEmpty() ? Set.of("spawn") : new HashSet<>(list));
   }

   public CuboidRegion region(String id) {
      return this.regions.get(id);
   }

   public boolean inSpawn(Location loc) {
      CuboidRegion cuboidregion = this.regions.get("spawn");
      return cuboidregion != null && cuboidregion.contains(loc);
   }

   public boolean inPvp(Location loc) {
      CuboidRegion cuboidregion = this.regions.get("pvp");
      return cuboidregion != null && cuboidregion.contains(loc);
   }

   public boolean inSide(Location loc) {
      if (loc != null && loc.getWorld() != null && !this.sideWorlds.isEmpty()) {
         if (!this.sideWorlds.contains(loc.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return false;
         } else {
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
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public boolean bypass(Player player) {
      return player.hasPermission("sharded.protect.bypass");
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

   private boolean restrictSideBreakUse(Player player, Location loc) {
      return this.bypass(player) ? false : this.inSide(loc);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.protect.admin")) {
            this.send(sender, "no-permission", new String[0]);
            return true;
         } else if (args.length == 0) {
            this.send(player, "usage", new String[0]);
            return true;
         } else {
            String s = args[0].toLowerCase(Locale.ROOT);
            if (s.equals("pos1")) {
               this.setup.setPos1(player, player.getLocation());
               this.send(player, "pos1-set", new String[0]);
               return true;
            } else if (s.equals("pos2")) {
               this.setup.setPos2(player, player.getLocation());
               this.send(player, "pos2-set", new String[0]);
               return true;
            } else if (s.equals("setregion") && args.length >= 2) {
               String s1 = args[1].toLowerCase(Locale.ROOT);
               if (!List.of("spawn", "pvp", "side1", "side2", "side3", "side4").contains(s1)) {
                  this.send(player, "unknown-region", new String[0]);
                  return true;
               } else {
                  CuboidRegion cuboidregion = this.setup.build(player);
                  if (cuboidregion == null) {
                     this.send(player, "need-positions", new String[0]);
                     return true;
                  } else {
                     this.regions.put(s1, cuboidregion);
                     cuboidregion.write(
                        this.config.getConfigurationSection("regions." + s1) != null
                           ? this.config.getConfigurationSection("regions." + s1)
                           : this.config.createSection("regions." + s1)
                     );
                     this.saveConfig();
                     this.send(player, "region-set", new String[]{"%region%", s1});
                     return true;
                  }
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
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      Location location = event.getBlock().getLocation();
      if (this.restrictSpawnPvp(player, location) || this.restrictSideBreakUse(player, location)) {
         event.setCancelled(true);
         this.send(player, "no-break", new String[0]);
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
   public void onPlace(BlockPlaceEvent event) {
      Player player = event.getPlayer();
      Location location = event.getBlock().getLocation();
      if (this.restrictSideBreakUse(player, location)) {
         int i = this.config.getInt("side-max-build-y", 111);
         if (location.getBlockY() > i) {
            event.setCancelled(true);
            this.send(player, "side-build-limit", new String[]{"%y%", String.valueOf(i)});
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
         Player player = event.getPlayer();
         if (this.hornBlocked(player)) {
            this.denyHorn(event, player);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onHornUseGuard(PlayerInteractEvent event) {
      if (this.isHornInteraction(event)) {
         Player player = event.getPlayer();
         if (this.hornBlocked(player)) {
            this.denyHorn(event, player);
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
         Player to = event.getPlayer();
         if (to instanceof Player) {
            Location location = event.getTo();
            if (location != null && !this.bypass(to) && !this.inSide(location)) {
               if (this.inSpawn(location)) {
                  event.setCancelled(true);
                  this.send(to, "no-pearl-spawn", new String[0]);
               }
            }
         }
      }
   }

   private void saveConfig() {
      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (Exception exception) {
         this.plugin.getLogger().warning("[protect] Could not save config: " + exception.getMessage());
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission("sharded.protect.admin")) {
         return List.of();
      } else if (args.length == 1) {
         return TabCompleteHelper.filter(args[0], "pos1", "pos2", "setregion");
      } else {
         return args.length == 2 && args[0].equalsIgnoreCase("setregion")
            ? TabCompleteHelper.filter(args[1], "spawn", "pvp", "side1", "side2", "side3", "side4")
            : List.of();
      }
   }
}
