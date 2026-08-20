package com.sharded.core.modules.staff;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.punishments.PunishmentsModule;
import com.sharded.core.modules.staffchat.StaffChatModule;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Staff mode inventory backup, tools, vanish, and freeze handling. */
public final class StaffModeManager implements Listener {

    private record StaffBackup(
            ItemStack[] contents,
            ItemStack[] armor,
            ItemStack offhand,
            GameMode gameMode,
            boolean flying,
            float flySpeed,
            float walkSpeed,
            boolean allowFlight
    ) {
    }

    private final ShardedCore plugin;
    private final StaffModule module;
    private final NamespacedKey staffItemKey;

    private static final String STAFF_MODE_STATE = "staff-mode-active";

    private final Set<UUID> staffMode = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, StaffBackup> backups = new ConcurrentHashMap<>();

    public StaffModeManager(ShardedCore plugin, StaffModule module) {
        this.plugin = plugin;
        this.module = module;
        this.staffItemKey = new NamespacedKey(plugin, "staff_item");
    }

    public boolean isStaffMode(UUID uuid) {
        return staffMode.contains(uuid);
    }

    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    public boolean isFrozen(UUID uuid) {
        return frozen.contains(uuid);
    }

    public void toggleStaffMode(Player player) {
        if (isStaffMode(player.getUniqueId())) disableStaffMode(player);
        else enableStaffMode(player);
    }

    public void enableStaffMode(Player player) {
        if (isStaffMode(player.getUniqueId())) return;
        backups.put(player.getUniqueId(), capture(player));
        clearInventory(player);
        giveStaffItems(player);
        player.setGameMode(GameMode.CREATIVE);
        if (module.config().getBoolean("staffmode.vanish-on-enter", true)) {
            setVanished(player, true, false);
        }
        staffMode.add(player.getUniqueId());
        disableEglow(player);
        hideStaffModePets(player);
        enableStaffChat(player);
        plugin.stateStore().setBool(player.getUniqueId(), STAFF_MODE_STATE, true);
        module.send(player, "staffmode-enabled");
    }

    public void disableStaffMode(Player player) {
        if (!isStaffMode(player.getUniqueId())) return;
        setVanished(player, false, false);
        StaffBackup backup = backups.remove(player.getUniqueId());
        player.getInventory().clear();
        if (backup != null) restore(player, backup);
        staffMode.remove(player.getUniqueId());
        disableStaffChat(player);
        restoreStaffModePets(player);
        plugin.stateStore().setBool(player.getUniqueId(), STAFF_MODE_STATE, false);
        module.send(player, "staffmode-disabled");
    }

    private void enableStaffChat(Player player) {
        StaffChatModule staffChat = plugin.modules().get(StaffChatModule.class);
        if (staffChat != null) staffChat.setEnabled(player, true, false);
    }

    private void disableStaffChat(Player player) {
        StaffChatModule staffChat = plugin.modules().get(StaffChatModule.class);
        if (staffChat != null) staffChat.setEnabled(player, false, false);
    }

    private void hideStaffModePets(Player player) {
        var pets = plugin.modules().get(com.sharded.core.modules.pets.PetsModule.class);
        if (pets != null) pets.hideEquippedPet(player);
    }

    private void restoreStaffModePets(Player player) {
        var pets = plugin.modules().get(com.sharded.core.modules.pets.PetsModule.class);
        if (pets != null) pets.restoreEquippedPet(player);
    }

    public void toggleVanish(Player player) {
        setVanished(player, !isVanished(player.getUniqueId()), true);
    }

    public void setVanished(Player player, boolean hide, boolean message) {
        UUID uuid = player.getUniqueId();
        if (hide) {
            player.setInvisible(true);
            if (module.config().getBoolean("vanish.fly", true)) {
                player.setAllowFlight(true);
                player.setFlying(true);
            }
            if (module.config().getBoolean("vanish.night-vision", false)) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!canSeeVanished(online)) online.hidePlayer(plugin, player);
            }
            vanished.add(uuid);
            if (message) module.send(player, "vanish-enabled");
        } else {
            player.setInvisible(false);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(plugin, player);
            }
            vanished.remove(uuid);
            if (message) module.send(player, "vanish-disabled");
        }
    }

    public void toggleFreeze(Player staff, Player target) {
        if (target == null) return;
        if (frozen.contains(target.getUniqueId())) {
            frozen.remove(target.getUniqueId());
            if (staff != null) module.send(staff, "freeze-disabled", "%player%", target.getName());
            module.send(target, "unfrozen");
        } else {
            frozen.add(target.getUniqueId());
            target.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            if (staff != null) module.send(staff, "freeze-enabled", "%player%", target.getName());
            module.send(target, "frozen");
        }
    }

    public void refreshVanishForJoin(Player joiner) {
        for (UUID uuid : vanished) {
            Player vanishedPlayer = Bukkit.getPlayer(uuid);
            if (vanishedPlayer == null || !vanishedPlayer.isOnline()) continue;
            if (!canSeeVanished(joiner)) joiner.hidePlayer(plugin, vanishedPlayer);
            else joiner.showPlayer(plugin, vanishedPlayer);
        }
    }

    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        if (isStaffMode(uuid)) {
            StaffBackup backup = backups.remove(uuid);
            if (backup != null) restore(player, backup);
            disableStaffChat(player);
        }
        staffMode.remove(uuid);
        vanished.remove(uuid);
        frozen.remove(uuid);
        plugin.stateStore().setBool(uuid, STAFF_MODE_STATE, false);
    }

    public List<Player> onlineStaff() {
        String perm = module.config().getString("staff-list-permission", "sharded.staff");
        List<Player> list = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(perm)) list.add(online);
        }
        return list;
    }

    public void teleportToRandomPlayer(Player staff) {
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(staff)) continue;
            if (online.hasPermission(module.config().getString("staff-list-permission", "sharded.staff"))) continue;
            if (isVanished(online.getUniqueId()) && !canSeeVanished(staff)) continue;
            candidates.add(online);
        }
        if (candidates.isEmpty()) {
            module.send(staff, "randomtp-none");
            return;
        }
        Player target = candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size()));
        staff.teleport(target.getLocation());
        module.send(staff, "randomtp", "%player%", target.getName());
    }

    private void disableEglow(Player player) {
        String cmd = module.config().getString("staffmode.disable-eglow-command", "eglow:eglow disable");
        if (cmd.startsWith("/")) player.performCommand(cmd.substring(1));
        else player.performCommand(cmd);
    }

    private boolean canSeeVanished(Player viewer) {
        if (isStaffMode(viewer.getUniqueId())) return true;
        return viewer.hasPermission(module.config().getString("vanish.see-permission", "sharded.staff.seevanished"));
    }

    private StaffBackup capture(Player player) {
        PlayerInventory inv = player.getInventory();
        return new StaffBackup(
                clone(inv.getContents()),
                clone(inv.getArmorContents()),
                inv.getItemInOffHand().clone(),
                player.getGameMode(),
                player.isFlying(),
                player.getFlySpeed(),
                player.getWalkSpeed(),
                player.getAllowFlight()
        );
    }

    private void restore(Player player, StaffBackup backup) {
        player.getInventory().setContents(clone(backup.contents()));
        player.getInventory().setArmorContents(clone(backup.armor()));
        player.getInventory().setItemInOffHand(backup.offhand());
        player.setGameMode(backup.gameMode());
        player.setAllowFlight(backup.allowFlight());
        player.setFlying(backup.flying());
        player.setFlySpeed(backup.flySpeed());
        player.setWalkSpeed(backup.walkSpeed());
        player.updateInventory();
    }

    private ItemStack[] clone(ItemStack[] items) {
        if (items == null) return new ItemStack[0];
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null ? null : items[i].clone();
        }
        return copy;
    }

    private void clearInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    private void giveStaffItems(Player player) {
        ConfigurationSection items = module.config().getConfigurationSection("staffmode.items");
        if (items == null) return;
        for (String key : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(key);
            if (section == null) continue;
            int slot = section.getInt("slot", -1);
            if (slot < 0) continue;
            ItemStack stack;
            if (key.equals("vanish")) {
                boolean hidden = isVanished(player.getUniqueId());
                stack = buildItem(section.getConfigurationSection(hidden ? "enabled" : "disabled"), key);
            } else {
                stack = buildItem(section, key);
            }
            if (stack != null) player.getInventory().setItem(slot, stack);
        }
    }

    private ItemStack buildItem(ConfigurationSection section, String key) {
        if (section == null) return null;
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) material = Material.STONE;
        ItemStack item = new ItemBuilder(material)
                .name(section.getString("display_name", key))
                .lore(section.getStringList("lore"))
                .hideAll()
                .build();
        item.editMeta(meta -> meta.getPersistentDataContainer().set(staffItemKey, PersistentDataType.STRING, key));
        return item;
    }

    private String staffItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(staffItemKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isStaffMode(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isStaffMode(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (staffItemId(event.getItemDrop().getItemStack()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (!isStaffMode(event.getPlayer().getUniqueId())) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isStaffMode(player.getUniqueId())) return;
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (staffItemId(current) != null || staffItemId(cursor) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && isVanished(player.getUniqueId())
                && module.config().getBoolean("vanish.disable-pickup", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        refreshVanishForJoin(player);
        if (plugin.stateStore().getBool(player.getUniqueId(), STAFF_MODE_STATE, false)
                || hasStaffItems(player)) {
            forceExitStaffMode(player);
        }
    }

    private void forceExitStaffMode(Player player) {
        UUID uuid = player.getUniqueId();
        staffMode.remove(uuid);
        backups.remove(uuid);
        setVanished(player, false, false);
        removeStaffItems(player);
        if (player.getGameMode() == GameMode.CREATIVE) player.setGameMode(GameMode.SURVIVAL);
        disableStaffChat(player);
        plugin.stateStore().setBool(uuid, STAFF_MODE_STATE, false);
    }

    private boolean hasStaffItems(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (staffItemId(item) != null) return true;
        }
        if (staffItemId(player.getInventory().getItemInOffHand()) != null) return true;
        return false;
    }

    private void removeStaffItems(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (staffItemId(contents[i]) != null) contents[i] = null;
        }
        inv.setContents(contents);
        if (staffItemId(inv.getItemInOffHand()) != null) inv.setItemInOffHand(null);
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (staffItemId(armor[i]) != null) armor[i] = null;
        }
        inv.setArmorContents(armor);
        player.updateInventory();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFreezeMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (!frozen.contains(event.getPlayer().getUniqueId())) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onStaffItemUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!isStaffMode(player.getUniqueId())) return;
        ItemStack item = event.getItem();
        String id = staffItemId(item);
        if (id == null) return;
        event.setCancelled(true);
        handleStaffItem(player, id, event.getClickedBlock() != null);
    }

    @EventHandler
    public void onStaffItemEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!isStaffMode(player.getUniqueId())) return;
        String id = staffItemId(event.getHand() == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand());
        if (id == null) return;
        event.setCancelled(true);
        if ("freeze".equals(id) && event.getRightClicked() instanceof Player target) {
            toggleFreeze(player, target);
            return;
        }
        if ("punish".equals(id) && event.getRightClicked() instanceof Player target) {
            PunishmentsModule punishments = plugin.modules().get(PunishmentsModule.class);
            if (punishments != null) punishments.openPunishMenu(player, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPunishAxe(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player staff)) return;
        if (!(event.getEntity() instanceof Player target)) return;
        if (!isStaffMode(staff.getUniqueId())) return;
        String id = staffItemId(staff.getInventory().getItemInMainHand());
        if (!"punish".equals(id)) return;
        event.setCancelled(true);
        PunishmentsModule punishments = plugin.modules().get(PunishmentsModule.class);
        if (punishments != null) punishments.openPunishMenu(staff, target);
    }

    private void handleStaffItem(Player player, String id, boolean leftClick) {
        switch (id.toLowerCase(Locale.ROOT)) {
            case "exit" -> disableStaffMode(player);
            case "vanish" -> toggleVanish(player);
            case "staff-list" -> showStaffList(player);
            case "random-teleport" -> teleportToRandomPlayer(player);
            case "freeze" -> module.send(player, "freeze-hint");
            case "punish" -> module.send(player, "punish-hint");
            default -> {
            }
        }
    }

    private void showStaffList(Player player) {
        List<Player> staff = onlineStaff();
        if (staff.isEmpty()) {
            module.send(player, "stafflist-empty");
            return;
        }
        module.send(player, "stafflist-header", "%count%", String.valueOf(staff.size()));
        for (Player member : staff) {
            String status = isVanished(member.getUniqueId()) ? module.raw("stafflist-vanished") : module.raw("stafflist-visible");
            module.send(player, "stafflist-entry", "%player%", member.getName(), "%status%", status);
        }
    }

    public void refreshVanishItem(Player player) {
        if (!isStaffMode(player.getUniqueId())) return;
        ConfigurationSection vanish = module.config().getConfigurationSection("staffmode.items.vanish");
        if (vanish == null) return;
        boolean hidden = isVanished(player.getUniqueId());
        ItemStack stack = buildItem(vanish.getConfigurationSection(hidden ? "enabled" : "disabled"), "vanish");
        if (stack != null) player.getInventory().setItem(vanish.getInt("slot", 0), stack);
    }
}
