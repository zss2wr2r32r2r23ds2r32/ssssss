package com.sharded.core.modules.staff;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.pets.PetsModule;
import com.sharded.core.modules.punishments.PunishmentsModule;
import com.sharded.core.modules.staffchat.StaffChatModule;
import com.sharded.core.util.ItemBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class StaffModeManager implements Listener {
   private final ShardedCore plugin;
   private final StaffModule module;
   private final NamespacedKey staffItemKey;
   private static final String STAFF_MODE_STATE = "staff-mode-active";
   private final Set<UUID> staffMode = ConcurrentHashMap.newKeySet();
   private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
   private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
   private final Map<UUID, StaffModeManager.StaffBackup> backups = new ConcurrentHashMap<>();

   public StaffModeManager(ShardedCore plugin, StaffModule module) {
      this.plugin = plugin;
      this.module = module;
      this.staffItemKey = new NamespacedKey(plugin, "staff_item");
   }

   public boolean isStaffMode(UUID uuid) {
      return this.staffMode.contains(uuid);
   }

   public boolean isVanished(UUID uuid) {
      return this.vanished.contains(uuid);
   }

   public boolean isFrozen(UUID uuid) {
      return this.frozen.contains(uuid);
   }

   public void toggleStaffMode(Player player) {
      if (this.isStaffMode(player.getUniqueId())) {
         this.disableStaffMode(player);
      } else {
         this.enableStaffMode(player);
      }
   }

   public void enableStaffMode(Player player) {
      if (!this.isStaffMode(player.getUniqueId())) {
         this.backups.put(player.getUniqueId(), this.capture(player));
         this.clearInventory(player);
         this.giveStaffItems(player);
         player.setGameMode(GameMode.CREATIVE);
         if (this.module.config().getBoolean("staffmode.vanish-on-enter", true)) {
            this.setVanished(player, true, false);
         }

         this.staffMode.add(player.getUniqueId());
         this.disableEglow(player);
         this.hideStaffModePets(player);
         this.enableStaffChat(player);
         this.plugin.stateStore().setBool(player.getUniqueId(), "staff-mode-active", true);
         this.module.send(player, "staffmode-enabled", new String[0]);
      }
   }

   public void disableStaffMode(Player player) {
      if (this.isStaffMode(player.getUniqueId())) {
         this.setVanished(player, false, false);
         StaffModeManager.StaffBackup staffmodemanager$staffbackup = this.backups.remove(player.getUniqueId());
         player.getInventory().clear();
         if (staffmodemanager$staffbackup != null) {
            this.restore(player, staffmodemanager$staffbackup);
         }

         this.staffMode.remove(player.getUniqueId());
         this.disableStaffChat(player);
         this.restoreStaffModePets(player);
         this.plugin.stateStore().setBool(player.getUniqueId(), "staff-mode-active", false);
         this.module.send(player, "staffmode-disabled", new String[0]);
      }
   }

   private void enableStaffChat(Player player) {
      StaffChatModule staffchatmodule = this.plugin.modules().get(StaffChatModule.class);
      if (staffchatmodule != null) {
         staffchatmodule.setEnabled(player, true, false);
      }
   }

   private void disableStaffChat(Player player) {
      StaffChatModule staffchatmodule = this.plugin.modules().get(StaffChatModule.class);
      if (staffchatmodule != null) {
         staffchatmodule.setEnabled(player, false, false);
      }
   }

   private void hideStaffModePets(Player player) {
      PetsModule petsmodule = this.plugin.modules().get(PetsModule.class);
      if (petsmodule != null) {
         petsmodule.hideEquippedPet(player);
      }
   }

   private void restoreStaffModePets(Player player) {
      PetsModule petsmodule = this.plugin.modules().get(PetsModule.class);
      if (petsmodule != null) {
         petsmodule.restoreEquippedPet(player);
      }
   }

   public void toggleVanish(Player player) {
      this.setVanished(player, !this.isVanished(player.getUniqueId()), true);
   }

   public void setVanished(Player player, boolean hide, boolean message) {
      UUID uuid = player.getUniqueId();
      if (hide) {
         player.setInvisible(true);
         if (this.module.config().getBoolean("vanish.fly", true)) {
            player.setAllowFlight(true);
            player.setFlying(true);
         }

         if (this.module.config().getBoolean("vanish.night-vision", false)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
         }

         for (Player playerx : Bukkit.getOnlinePlayers()) {
            if (!this.canSeeVanished(playerx)) {
               playerx.hidePlayer(this.plugin, player);
            }
         }

         this.vanished.add(uuid);
         if (message) {
            this.module.send(player, "vanish-enabled", new String[0]);
         }
      } else {
         player.setInvisible(false);
         player.removePotionEffect(PotionEffectType.NIGHT_VISION);

         for (Player player1 : Bukkit.getOnlinePlayers()) {
            player1.showPlayer(this.plugin, player);
         }

         this.vanished.remove(uuid);
         if (message) {
            this.module.send(player, "vanish-disabled", new String[0]);
         }
      }
   }

   public void toggleFreeze(Player staff, Player target) {
      if (target != null) {
         if (this.frozen.contains(target.getUniqueId())) {
            this.frozen.remove(target.getUniqueId());
            if (staff != null) {
               this.module.send(staff, "freeze-disabled", new String[]{"%player%", target.getName()});
            }

            this.module.send(target, "unfrozen", new String[0]);
         } else {
            this.frozen.add(target.getUniqueId());
            target.setVelocity(new Vector(0, 0, 0));
            if (staff != null) {
               this.module.send(staff, "freeze-enabled", new String[]{"%player%", target.getName()});
            }

            this.module.send(target, "frozen", new String[0]);
         }
      }
   }

   public void refreshVanishForJoin(Player joiner) {
      for (UUID uuid : this.vanished) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null && player.isOnline()) {
            if (!this.canSeeVanished(joiner)) {
               joiner.hidePlayer(this.plugin, player);
            } else {
               joiner.showPlayer(this.plugin, player);
            }
         }
      }
   }

   public void cleanup(Player player) {
      UUID uuid = player.getUniqueId();
      if (this.isVanished(uuid) || player.isInvisible()) {
         this.setVanished(player, false, false);
      }

      if (this.isStaffMode(uuid)) {
         StaffModeManager.StaffBackup staffmodemanager$staffbackup = this.backups.remove(uuid);
         if (staffmodemanager$staffbackup != null) {
            this.restore(player, staffmodemanager$staffbackup);
         }

         this.disableStaffChat(player);
      }

      this.staffMode.remove(uuid);
      this.vanished.remove(uuid);
      this.frozen.remove(uuid);
      this.plugin.stateStore().setBool(uuid, "staff-mode-active", false);
   }

   public List<Player> onlineStaff() {
      String s = this.module.config().getString("staff-list-permission", "sharded.staff");
      List<Player> list = new ArrayList<>();

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (player.hasPermission(s)) {
            list.add(player);
         }
      }

      return list;
   }

   public void teleportToRandomPlayer(Player staff) {
      List<Player> list = new ArrayList<>();

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (!player.equals(staff)
            && !player.hasPermission(this.module.config().getString("staff-list-permission", "sharded.staff"))
            && (!this.isVanished(player.getUniqueId()) || this.canSeeVanished(staff))) {
            list.add(player);
         }
      }

      if (list.isEmpty()) {
         this.module.send(staff, "randomtp-none", new String[0]);
      } else {
         Player player1 = list.get(ThreadLocalRandom.current().nextInt(list.size()));
         staff.teleport(player1.getLocation());
         this.module.send(staff, "randomtp", new String[]{"%player%", player1.getName()});
      }
   }

   private void disableEglow(Player player) {
      String s = this.module.config().getString("staffmode.disable-eglow-command", "eglow:eglow disable");
      if (s.startsWith("/")) {
         player.performCommand(s.substring(1));
      } else {
         player.performCommand(s);
      }
   }

   private boolean canSeeVanished(Player viewer) {
      return this.isStaffMode(viewer.getUniqueId())
         ? true
         : viewer.hasPermission(this.module.config().getString("vanish.see-permission", "sharded.staff.seevanished"));
   }

   private StaffModeManager.StaffBackup capture(Player player) {
      PlayerInventory playerinventory = player.getInventory();
      return new StaffModeManager.StaffBackup(
         this.clone(playerinventory.getContents()),
         this.clone(playerinventory.getArmorContents()),
         playerinventory.getItemInOffHand().clone(),
         player.getGameMode(),
         player.isFlying(),
         player.getFlySpeed(),
         player.getWalkSpeed(),
         player.getAllowFlight()
      );
   }

   private void restore(Player player, StaffModeManager.StaffBackup backup) {
      player.getInventory().setContents(this.clone(backup.contents()));
      player.getInventory().setArmorContents(this.clone(backup.armor()));
      player.getInventory().setItemInOffHand(backup.offhand());
      player.setGameMode(backup.gameMode());
      player.setAllowFlight(backup.allowFlight());
      player.setFlying(backup.flying());
      player.setFlySpeed(backup.flySpeed());
      player.setWalkSpeed(backup.walkSpeed());
      player.updateInventory();
   }

   private ItemStack[] clone(ItemStack[] items) {
      if (items == null) {
         return new ItemStack[0];
      } else {
         ItemStack[] aitemstack = new ItemStack[items.length];

         for (int i = 0; i < items.length; i++) {
            aitemstack[i] = items[i] == null ? null : items[i].clone();
         }

         return aitemstack;
      }
   }

   private void clearInventory(Player player) {
      player.getInventory().clear();
      player.getInventory().setArmorContents(null);
      player.getInventory().setItemInOffHand(null);
      player.updateInventory();
   }

   private void giveStaffItems(Player player) {
      ConfigurationSection configurationsection = this.module.config().getConfigurationSection("staffmode.items");
      if (configurationsection != null) {
         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            if (configurationsection1 != null) {
               int i = configurationsection1.getInt("slot", -1);
               if (i >= 0) {
                  ItemStack itemstack;
                  if (s.equals("vanish")) {
                     boolean flag = this.isVanished(player.getUniqueId());
                     itemstack = this.buildItem(configurationsection1.getConfigurationSection(flag ? "enabled" : "disabled"), s);
                  } else {
                     itemstack = this.buildItem(configurationsection1, s);
                  }

                  if (itemstack != null) {
                     player.getInventory().setItem(i, itemstack);
                  }
               }
            }
         }
      }
   }

   private ItemStack buildItem(ConfigurationSection section, String key) {
      if (section == null) {
         return null;
      } else {
         Material material = Material.matchMaterial(section.getString("material", "STONE"));
         if (material == null) {
            material = Material.STONE;
         }

         ItemStack itemstack = new ItemBuilder(material).name(section.getString("display_name", key)).lore(section.getStringList("lore")).hideAll().build();
         itemstack.editMeta(meta -> meta.getPersistentDataContainer().set(this.staffItemKey, PersistentDataType.STRING, key));
         return itemstack;
      }
   }

   private String staffItemId(ItemStack item) {
      return item != null && item.hasItemMeta()
         ? (String)item.getItemMeta().getPersistentDataContainer().get(this.staffItemKey, PersistentDataType.STRING)
         : null;
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent event) {
      if (this.isStaffMode(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent event) {
      if (this.isStaffMode(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onDrop(PlayerDropItemEvent event) {
      if (this.staffItemId(event.getItemDrop().getItemStack()) != null) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onDeath(PlayerDeathEvent event) {
      if (this.isStaffMode(event.getPlayer().getUniqueId())) {
         event.setKeepInventory(true);
         event.getDrops().clear();
         event.setDroppedExp(0);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         if (this.isStaffMode(player.getUniqueId())) {
            ItemStack itemstack1 = event.getCurrentItem();
            ItemStack itemstack = event.getCursor();
            if (this.staffItemId(itemstack1) != null || this.staffItemId(itemstack) != null) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onPickup(EntityPickupItemEvent event) {
      if (event.getEntity() instanceof Player player && this.isVanished(player.getUniqueId()) && this.module.config().getBoolean("vanish.disable-pickup", true)
         )
       {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      if (player.isInvisible() && !this.vanished.contains(player.getUniqueId())) {
         player.setInvisible(false);

         for (Player player1 : Bukkit.getOnlinePlayers()) {
            if (!player1.equals(player)) {
               player1.showPlayer(this.plugin, player);
            }
         }
      }

      this.refreshVanishForJoin(player);
      if (this.plugin.stateStore().getBool(player.getUniqueId(), "staff-mode-active", false) || this.hasStaffItems(player)) {
         this.forceExitStaffMode(player);
      }
   }

   private void forceExitStaffMode(Player player) {
      UUID uuid = player.getUniqueId();
      this.staffMode.remove(uuid);
      this.backups.remove(uuid);
      this.setVanished(player, false, false);
      this.removeStaffItems(player);
      if (player.getGameMode() == GameMode.CREATIVE) {
         player.setGameMode(GameMode.SURVIVAL);
      }

      this.disableStaffChat(player);
      this.plugin.stateStore().setBool(uuid, "staff-mode-active", false);
   }

   private boolean hasStaffItems(Player player) {
      for (ItemStack itemstack : player.getInventory().getContents()) {
         if (this.staffItemId(itemstack) != null) {
            return true;
         }
      }

      return this.staffItemId(player.getInventory().getItemInOffHand()) != null;
   }

   private void removeStaffItems(Player player) {
      PlayerInventory playerinventory = player.getInventory();
      ItemStack[] aitemstack = playerinventory.getContents();

      for (int i = 0; i < aitemstack.length; i++) {
         if (this.staffItemId(aitemstack[i]) != null) {
            aitemstack[i] = null;
         }
      }

      playerinventory.setContents(aitemstack);
      if (this.staffItemId(playerinventory.getItemInOffHand()) != null) {
         playerinventory.setItemInOffHand(null);
      }

      ItemStack[] aitemstack1 = playerinventory.getArmorContents();

      for (int j = 0; j < aitemstack1.length; j++) {
         if (this.staffItemId(aitemstack1[j]) != null) {
            aitemstack1[j] = null;
         }
      }

      playerinventory.setArmorContents(aitemstack1);
      player.updateInventory();
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.cleanup(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onFreezeMove(PlayerMoveEvent event) {
      if (this.frozen.contains(event.getPlayer().getUniqueId())) {
         if (event.getFrom().getBlockX() != event.getTo().getBlockX()
            || event.getFrom().getBlockY() != event.getTo().getBlockY()
            || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom());
         }
      }
   }

   @EventHandler
   public void onStaffItemUse(PlayerInteractEvent event) {
      if (event.getHand() == EquipmentSlot.HAND) {
         Player player = event.getPlayer();
         if (this.isStaffMode(player.getUniqueId())) {
            ItemStack itemstack = event.getItem();
            String s = this.staffItemId(itemstack);
            if (s != null) {
               event.setCancelled(true);
               this.handleStaffItem(player, s, event.getClickedBlock() != null);
            }
         }
      }
   }

   @EventHandler
   public void onStaffItemEntity(PlayerInteractEntityEvent event) {
      if (event.getHand() == EquipmentSlot.HAND) {
         Player player = event.getPlayer();
         if (this.isStaffMode(player.getUniqueId())) {
            String s = this.staffItemId(
               event.getHand() == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand()
            );
            if (s != null) {
               event.setCancelled(true);
               if ("freeze".equals(s) && event.getRightClicked() instanceof Player player2) {
                  this.toggleFreeze(player, player2);
               } else {
                  if ("punish".equals(s) && event.getRightClicked() instanceof Player player1) {
                     PunishmentsModule punishmentsmodule = this.plugin.modules().get(PunishmentsModule.class);
                     if (punishmentsmodule != null) {
                        punishmentsmodule.openPunishMenu(player, player1);
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
   public void onPunishAxe(EntityDamageByEntityEvent event) {
      if (event.getDamager() instanceof Player player) {
         if (event.getEntity() instanceof Player player1) {
            if (this.isStaffMode(player.getUniqueId())) {
               String s = this.staffItemId(player.getInventory().getItemInMainHand());
               if ("punish".equals(s)) {
                  event.setCancelled(true);
                  PunishmentsModule punishmentsmodule = this.plugin.modules().get(PunishmentsModule.class);
                  if (punishmentsmodule != null) {
                     punishmentsmodule.openPunishMenu(player, player1);
                  }
               }
            }
         }
      }
   }

   private void handleStaffItem(Player player, String id, boolean leftClick) {
      String s = id.toLowerCase(Locale.ROOT);
      switch (s) {
         case "exit":
            this.disableStaffMode(player);
            break;
         case "vanish":
            this.toggleVanish(player);
            break;
         case "staff-list":
            this.showStaffList(player);
            break;
         case "random-teleport":
            this.teleportToRandomPlayer(player);
            break;
         case "freeze":
            this.module.send(player, "freeze-hint", new String[0]);
            break;
         case "punish":
            this.module.send(player, "punish-hint", new String[0]);
      }
   }

   private void showStaffList(Player player) {
      List<Player> list = this.onlineStaff();
      if (list.isEmpty()) {
         this.module.send(player, "stafflist-empty", new String[0]);
      } else {
         this.module.send(player, "stafflist-header", new String[]{"%count%", String.valueOf(list.size())});

         for (Player playerx : list) {
            String s = this.isVanished(playerx.getUniqueId())
               ? this.module.raw("stafflist-vanished", new String[0])
               : this.module.raw("stafflist-visible", new String[0]);
            this.module.send(player, "stafflist-entry", new String[]{"%player%", playerx.getName(), "%status%", s});
         }
      }
   }

   public void refreshVanishItem(Player player) {
      if (this.isStaffMode(player.getUniqueId())) {
         ConfigurationSection configurationsection = this.module.config().getConfigurationSection("staffmode.items.vanish");
         if (configurationsection != null) {
            boolean flag = this.isVanished(player.getUniqueId());
            ItemStack itemstack = this.buildItem(configurationsection.getConfigurationSection(flag ? "enabled" : "disabled"), "vanish");
            if (itemstack != null) {
               player.getInventory().setItem(configurationsection.getInt("slot", 0), itemstack);
            }
         }
      }
   }

   private static record StaffBackup(
      ItemStack[] contents, ItemStack[] armor, ItemStack offhand, GameMode gameMode, boolean flying, float flySpeed, float walkSpeed, boolean allowFlight
   ) {
   }
}
