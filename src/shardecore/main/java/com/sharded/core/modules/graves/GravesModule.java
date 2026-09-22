package com.sharded.core.modules.graves;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.wardrobe.WardrobeModule;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

public final class GravesModule extends Module implements CommandExecutor, TabCompleter {
   private final Map<UUID, GravesModule.Grave> graves = new HashMap<>();
   private NamespacedKey graveKey;
   private File gravesFile;
   private BukkitTask timerTask;

   public GravesModule(ShardedCore plugin) {
      super(plugin, "graves");
   }

   @Override
   protected void onEnable() {
      this.graveKey = new NamespacedKey(this.plugin, "grave-id");
      this.gravesFile = new File(this.moduleFolder(), "graves.yml");
      this.loadGraves();

      for (GravesModule.Grave gravesmodule$grave : new ArrayList<>(this.graves.values())) {
         if (gravesmodule$grave.expired()) {
            this.removeGrave(gravesmodule$grave, true);
         } else {
            this.placeHead(gravesmodule$grave);
            this.spawnHologram(gravesmodule$grave);
         }
      }

      this.registerCommand("graves", this);
      this.registerListener(this);
      this.timerTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 20L, 20L);
   }

   @Override
   protected void onDisable() {
      if (this.timerTask != null) {
         this.timerTask.cancel();
         this.timerTask = null;
      }

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (player.getOpenInventory().getTopInventory().getHolder() instanceof GravesModule.GraveHolder) {
            player.closeInventory();
         }
      }

      for (GravesModule.Grave gravesmodule$grave : this.graves.values()) {
         this.removeHologram(gravesmodule$grave);
         gravesmodule$grave.opening = false;
         gravesmodule$grave.viewer = null;
      }

      this.saveGraves();
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      if (player.hasPermission("sharded.graves.use")) {
         List<ItemStack> list = this.cloneItems(event.getDrops());
         list.removeIf(this::isCosmeticHelmet);
         if (!list.isEmpty()) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            Location location = this.graveLocation(player.getLocation());
            long i = System.currentTimeMillis();
            long j = Math.max(1L, this.config.getLong("lifetime-seconds", 3600L));
            GravesModule.Grave gravesmodule$grave = new GravesModule.Grave(
               UUID.randomUUID(), player.getUniqueId(), player.getName(), location, list, i, i + j * 1000L
            );
            this.graves.put(gravesmodule$grave.id, gravesmodule$grave);
            this.placeHead(gravesmodule$grave);
            this.spawnHologram(gravesmodule$grave);
            this.saveGraves();
            this.send(
               player,
               "created",
               new String[]{
                  "%x%",
                  String.valueOf(location.getBlockX()),
                  "%y%",
                  String.valueOf(location.getBlockY()),
                  "%z%",
                  String.valueOf(location.getBlockZ()),
                  "%time%",
                  this.formatTime(gravesmodule$grave.secondsLeft())
               }
            );
         }
      }
   }

   private Location graveLocation(Location death) {
      Location location = death.getBlock().getLocation();
      if (!this.isMarkedGrave(location.getBlock())) {
         return location;
      } else {
         for (int i = 1; i <= 3; i++) {
            Block block = location.clone().add(0.0, (double)i, 0.0).getBlock();
            if (!this.isMarkedGrave(block)) {
               return block.getLocation();
            }
         }

         return location.clone().add(1.0, 0.0, 0.0).getBlock().getLocation();
      }
   }

   private void placeHead(GravesModule.Grave grave) {
      Block block = grave.location.getBlock();
      block.setType(Material.PLAYER_HEAD, false);
      if (block.getState() instanceof Skull skull) {
         skull.setOwningPlayer(Bukkit.getOfflinePlayer(grave.owner));
         skull.getPersistentDataContainer().set(this.graveKey, PersistentDataType.STRING, grave.id.toString());
         skull.update(true, false);
      }
   }

   private void spawnHologram(GravesModule.Grave grave) {
      this.removeHologram(grave);
      World world = grave.location.getWorld();
      if (world != null) {
         Location location = grave.location.clone().add(0.5, 1.45, 0.5);
         TextDisplay textdisplay = (TextDisplay)world.spawn(location, TextDisplay.class, entity -> {
            entity.setBillboard(Billboard.CENTER);
            entity.setPersistent(false);
            entity.setShadowed(true);
            entity.setSeeThrough(false);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.getPersistentDataContainer().set(this.graveKey, PersistentDataType.STRING, grave.id.toString());
         });
         grave.hologramId = textdisplay.getUniqueId();
         this.updateHologram(grave, textdisplay);
      }
   }

   private void updateHologram(GravesModule.Grave grave, TextDisplay display) {
      List<String> list = this.config.getStringList("hologram-lines");
      if (list.isEmpty()) {
         list = this.config.getStringList("hologram.lines");
      }

      if (list.isEmpty()) {
         list = List.of("&#5C94FC&l%player%'s Grave", "&fExpires in &#FFB800%time%");
      }

      List<String> list1 = new ArrayList<>();

      for (String s : list) {
         list1.add(s.replace("%player%", grave.ownerName).replace("%owner%", grave.ownerName).replace("%time%", this.formatTime(grave.secondsLeft())));
      }

      display.text(Text.c(String.join("\n", list1)));
   }

   private void tick() {
      for (GravesModule.Grave gravesmodule$grave : new ArrayList<>(this.graves.values())) {
         if (gravesmodule$grave.expired()) {
            this.removeGrave(gravesmodule$grave, true);
            Player player = Bukkit.getPlayer(gravesmodule$grave.owner);
            if (player != null) {
               this.send(player, "expired", new String[0]);
            }
         } else {
            Entity entity = gravesmodule$grave.hologramId == null ? null : Bukkit.getEntity(gravesmodule$grave.hologramId);
            if (entity instanceof TextDisplay) {
               TextDisplay textdisplay = (TextDisplay)entity;
               if (textdisplay.isValid()) {
                  this.updateHologram(gravesmodule$grave, textdisplay);
                  continue;
               }
            }

            this.spawnHologram(gravesmodule$grave);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onInteract(PlayerInteractEvent event) {
      if (event.getHand() == EquipmentSlot.HAND && event.getClickedBlock() != null) {
         GravesModule.Grave gravesmodule$grave = this.graveFrom(event.getClickedBlock());
         if (gravesmodule$grave != null) {
            event.setCancelled(true);
            this.openGrave(event.getPlayer(), gravesmodule$grave);
         }
      }
   }

   private void openGrave(Player player, GravesModule.Grave grave) {
      if (grave.expired()) {
         this.removeGrave(grave, true);
         this.send(player, "expired", new String[0]);
      } else if (!grave.owner.equals(player.getUniqueId()) && !player.hasPermission("sharded.graves.admin") && !player.hasPermission("sharded.graves.bypass")) {
         this.send(player, "not-yours", new String[0]);
      } else if (grave.opening) {
         this.send(player, "already-open", new String[0]);
      } else {
         GravesModule.GraveHolder gravesmodule$graveholder = new GravesModule.GraveHolder(grave.id);
         String s = this.config.getString("gui-title", "&8%player%'s Grave").replace("%player%", grave.ownerName);
         Inventory inventory = Bukkit.createInventory(gravesmodule$graveholder, 54, Text.c(s));
         gravesmodule$graveholder.inventory = inventory;

         for (int i = 0; i < Math.min(inventory.getSize(), grave.items.size()); i++) {
            inventory.setItem(i, grave.items.get(i).clone());
         }

         grave.opening = true;
         grave.viewer = player.getUniqueId();
         player.openInventory(inventory);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof GravesModule.GraveHolder gravesmodule$graveholder) {
         GravesModule.Grave gravesmodule$grave = this.graves.get(gravesmodule$graveholder.graveId);
         if (gravesmodule$grave != null && gravesmodule$grave.viewer != null && gravesmodule$grave.viewer.equals(event.getWhoClicked().getUniqueId())) {
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
               InventoryAction inventoryaction = event.getAction();
               boolean flag = inventoryaction == InventoryAction.PICKUP_ALL
                  || inventoryaction == InventoryAction.PICKUP_HALF
                  || inventoryaction == InventoryAction.PICKUP_ONE
                  || inventoryaction == InventoryAction.PICKUP_SOME
                  || inventoryaction == InventoryAction.MOVE_TO_OTHER_INVENTORY
                  || inventoryaction == InventoryAction.DROP_ALL_SLOT
                  || inventoryaction == InventoryAction.DROP_ONE_SLOT;
               if (!flag) {
                  event.setCancelled(true);
               }
            } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
               event.setCancelled(true);
            }
         } else {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onInventoryDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof GravesModule.GraveHolder) {
         int i = event.getView().getTopInventory().getSize();

         for (int j : event.getRawSlots()) {
            if (j < i) {
               event.setCancelled(true);
               return;
            }
         }
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      if (event.getInventory().getHolder() instanceof GravesModule.GraveHolder gravesmodule$graveholder) {
         GravesModule.Grave gravesmodule$grave = this.graves.get(gravesmodule$graveholder.graveId);
         if (gravesmodule$grave != null
            && !gravesmodule$grave.removing
            && gravesmodule$grave.viewer != null
            && gravesmodule$grave.viewer.equals(event.getPlayer().getUniqueId())) {
            this.syncFromInventory(gravesmodule$grave, event.getInventory());
            gravesmodule$grave.opening = false;
            gravesmodule$grave.viewer = null;
            if (gravesmodule$grave.items.isEmpty()) {
               this.removeGrave(gravesmodule$grave, false);
               this.send(event.getPlayer(), "claimed", new String[0]);
            } else {
               this.saveGraves();
            }
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      Inventory inventory = event.getPlayer().getOpenInventory().getTopInventory();
      if (inventory.getHolder() instanceof GravesModule.GraveHolder gravesmodule$graveholder) {
         GravesModule.Grave gravesmodule$grave = this.graves.get(gravesmodule$graveholder.graveId);
         if (gravesmodule$grave != null && gravesmodule$grave.viewer != null && gravesmodule$grave.viewer.equals(event.getPlayer().getUniqueId())) {
            this.syncFromInventory(gravesmodule$grave, inventory);
            gravesmodule$grave.opening = false;
            gravesmodule$grave.viewer = null;
            if (gravesmodule$grave.items.isEmpty()) {
               this.removeGrave(gravesmodule$grave, false);
            } else {
               this.saveGraves();
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent event) {
      GravesModule.Grave gravesmodule$grave = this.graveFrom(event.getBlock());
      if (gravesmodule$grave != null) {
         event.setCancelled(true);
         this.send(event.getPlayer(), "break-denied", new String[0]);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onEntityExplode(EntityExplodeEvent event) {
      event.blockList().removeIf(this::isMarkedGrave);
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onBlockExplode(BlockExplodeEvent event) {
      event.blockList().removeIf(this::isMarkedGrave);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length != 0 && !args[0].equalsIgnoreCase("list")) {
         if (args.length != 1 || !args[0].equalsIgnoreCase("purge")) {
            this.send(sender, "usage", new String[0]);
            return true;
         } else if (!sender.hasPermission("sharded.graves.admin")) {
            this.send(sender, "no-permission", new String[0]);
            return true;
         } else {
            int i = this.graves.size();

            for (GravesModule.Grave gravesmodule$grave1 : new ArrayList<>(this.graves.values())) {
               this.removeGrave(gravesmodule$grave1, false);
            }

            this.send(sender, "purged", new String[]{"%count%", String.valueOf(i)});
            return true;
         }
      } else {
         List<GravesModule.Grave> list = new ArrayList<>();
         UUID uuid = sender instanceof Player player ? player.getUniqueId() : null;
         boolean flag = sender.hasPermission("sharded.graves.admin");

         for (GravesModule.Grave gravesmodule$grave : this.graves.values()) {
            if (flag || gravesmodule$grave.owner.equals(uuid)) {
               list.add(gravesmodule$grave);
            }
         }

         this.send(sender, "list-header", new String[]{"%count%", String.valueOf(list.size())});

         for (GravesModule.Grave gravesmodule$grave2 : list) {
            this.send(
               sender,
               "list-line",
               new String[]{
                  "%player%",
                  gravesmodule$grave2.ownerName,
                  "%world%",
                  gravesmodule$grave2.location.getWorld() == null ? "unknown" : gravesmodule$grave2.location.getWorld().getName(),
                  "%x%",
                  String.valueOf(gravesmodule$grave2.location.getBlockX()),
                  "%y%",
                  String.valueOf(gravesmodule$grave2.location.getBlockY()),
                  "%z%",
                  String.valueOf(gravesmodule$grave2.location.getBlockZ()),
                  "%time%",
                  this.formatTime(gravesmodule$grave2.secondsLeft())
               }
            );
         }

         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length != 1) {
         return List.of();
      } else {
         return sender.hasPermission("sharded.graves.admin") ? TabCompleteHelper.filter(args[0], "list", "purge") : TabCompleteHelper.filter(args[0], "list");
      }
   }

   private GravesModule.Grave graveFrom(Block block) {
      if (block.getState() instanceof Skull skull) {
         String s = (String)skull.getPersistentDataContainer().get(this.graveKey, PersistentDataType.STRING);
         if (s == null) {
            return null;
         } else {
            try {
               return this.graves.get(UUID.fromString(s));
            } catch (IllegalArgumentException illegalargumentexception) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private boolean isMarkedGrave(Block block) {
      return this.graveFrom(block) != null;
   }

   private void syncFromInventory(GravesModule.Grave grave, Inventory inventory) {
      grave.items.clear();
      grave.items.addAll(this.cloneItems(Arrays.asList(inventory.getContents())));
   }

   private boolean isCosmeticHelmet(ItemStack item) {
      WardrobeModule wardrobe = this.plugin.modules().get(WardrobeModule.class);
      return wardrobe != null && wardrobe.isWardrobeHat(item);
   }

   private List<ItemStack> cloneItems(Iterable<ItemStack> items) {
      List<ItemStack> list = new ArrayList<>();

      for (ItemStack itemstack : items) {
         if (itemstack != null && !itemstack.getType().isAir() && itemstack.getAmount() > 0 && !this.isCosmeticHelmet(itemstack)) {
            list.add(itemstack.clone());
         }
      }

      return list;
   }

   private void removeGrave(GravesModule.Grave grave, boolean dropItems) {
      if (!grave.removing) {
         grave.removing = true;
         if (grave.viewer != null) {
            Player player = Bukkit.getPlayer(grave.viewer);
            if (player != null
               && player.getOpenInventory().getTopInventory().getHolder() instanceof GravesModule.GraveHolder gravesmodule$graveholder
               && gravesmodule$graveholder.graveId.equals(grave.id)) {
               this.syncFromInventory(grave, player.getOpenInventory().getTopInventory());
               player.closeInventory();
            }
         }

         this.graves.remove(grave.id);
         grave.opening = false;
         grave.viewer = null;
         Block block = grave.location.getBlock();
         if (this.isHeadFor(block, grave.id)) {
            block.setType(Material.AIR, false);
         }

         this.removeHologram(grave);
         if (dropItems && grave.location.getWorld() != null) {
            for (ItemStack itemstack : grave.items) {
               grave.location.getWorld().dropItemNaturally(grave.location.clone().add(0.5, 0.5, 0.5), itemstack.clone());
            }
         }

         grave.items.clear();
         this.saveGraves();
      }
   }

   private boolean isHeadFor(Block block, UUID graveId) {
      if (block.getState() instanceof Skull skull) {
         String s = (String)skull.getPersistentDataContainer().get(this.graveKey, PersistentDataType.STRING);
         return graveId.toString().equals(s);
      } else {
         return false;
      }
   }

   private void removeHologram(GravesModule.Grave grave) {
      if (grave.hologramId != null) {
         Entity entity = Bukkit.getEntity(grave.hologramId);
         if (entity != null) {
            entity.remove();
         }

         grave.hologramId = null;
      }
   }

   private String formatTime(long seconds) {
      long i = Math.max(0L, seconds);
      long j = i / 3600L;
      long k = i % 3600L / 60L;
      long l = i % 60L;
      return j > 0L ? String.format("%d:%02d:%02d", j, k, l) : String.format("%02d:%02d", k, l);
   }

   private void loadGraves() {
      this.graves.clear();
      if (this.gravesFile.isFile()) {
         YamlConfiguration yamlconfiguration = YamlConfiguration.loadConfiguration(this.gravesFile);
         ConfigurationSection configurationsection = yamlconfiguration.getConfigurationSection("graves");
         if (configurationsection != null) {
            for (String s : configurationsection.getKeys(false)) {
               try {
                  String s1 = "graves." + s;
                  UUID uuid = UUID.fromString(s);
                  UUID uuid1 = UUID.fromString(yamlconfiguration.getString(s1 + ".owner", ""));
                  String s2 = yamlconfiguration.getString(s1 + ".owner-name", "Unknown");
                  Location location = yamlconfiguration.getLocation(s1 + ".location");
                  if (location != null && location.getWorld() != null) {
                     List<ItemStack> list = new ArrayList<>();

                     for (Object object : yamlconfiguration.getList(s1 + ".items", List.of())) {
                        if (object instanceof ItemStack) {
                           ItemStack itemstack = (ItemStack)object;
                           if (!itemstack.getType().isAir()) {
                              list.add(itemstack.clone());
                           }
                        }
                     }

                     if (!list.isEmpty()) {
                        long i = yamlconfiguration.getLong(s1 + ".created-at", System.currentTimeMillis());
                        long j = yamlconfiguration.getLong(s1 + ".expires-at", i + Math.max(1L, this.config.getLong("lifetime-seconds", 3600L)) * 1000L);
                        this.graves.put(uuid, new GravesModule.Grave(uuid, uuid1, s2, location, list, i, j));
                     }
                  }
               } catch (IllegalArgumentException illegalargumentexception) {
                  this.plugin.getLogger().warning("[graves] Skipping invalid grave " + s);
               }
            }
         }
      }
   }

   private void saveGraves() {
      if (this.gravesFile != null) {
         YamlConfiguration yamlconfiguration = new YamlConfiguration();

         for (GravesModule.Grave gravesmodule$grave : this.graves.values()) {
            if (!gravesmodule$grave.items.isEmpty() && !gravesmodule$grave.removing) {
               String s = "graves." + gravesmodule$grave.id;
               yamlconfiguration.set(s + ".owner", gravesmodule$grave.owner.toString());
               yamlconfiguration.set(s + ".owner-name", gravesmodule$grave.ownerName);
               yamlconfiguration.set(s + ".location", gravesmodule$grave.location);
               yamlconfiguration.set(s + ".created-at", gravesmodule$grave.createdAt);
               yamlconfiguration.set(s + ".expires-at", gravesmodule$grave.expiresAt);
               yamlconfiguration.set(s + ".items", this.cloneItems(gravesmodule$grave.items));
            }
         }

         try {
            yamlconfiguration.save(this.gravesFile);
         } catch (IOException ioexception) {
            this.plugin.getLogger().severe("[graves] Could not save graves.yml: " + ioexception.getMessage());
         }
      }
   }

   private static final class Grave {
      private final UUID id;
      private final UUID owner;
      private final String ownerName;
      private final Location location;
      private final List<ItemStack> items;
      private final long createdAt;
      private final long expiresAt;
      private UUID hologramId;
      private UUID viewer;
      private boolean opening;
      private boolean removing;

      private Grave(UUID id, UUID owner, String ownerName, Location location, List<ItemStack> items, long createdAt, long expiresAt) {
         this.id = id;
         this.owner = owner;
         this.ownerName = ownerName;
         this.location = location;
         this.items = items;
         this.createdAt = createdAt;
         this.expiresAt = expiresAt;
      }

      private long secondsLeft() {
         return Math.max(0L, (this.expiresAt - System.currentTimeMillis() + 999L) / 1000L);
      }

      private boolean expired() {
         return System.currentTimeMillis() >= this.expiresAt;
      }
   }

   private static final class GraveHolder implements InventoryHolder {
      private final UUID graveId;
      private Inventory inventory;

      private GraveHolder(UUID graveId) {
         this.graveId = graveId;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
