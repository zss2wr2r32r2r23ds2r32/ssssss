package com.sharded.core.modules.wardrobe;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.ItemsAdderHook;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Cosmetic hat wardrobe — equip ItemsAdder hats as helmets with enchants. */
public final class WardrobeModule extends Module implements CommandExecutor {

    private static final String MENU_TITLE = "Wardrobe";

    public static final class MenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final Map<String, HatOption> hats = new LinkedHashMap<>();
    private final Set<UUID> hatWearers = new HashSet<>();
    private WardrobeDatabase database;
    private NamespacedKey hatKey;
    private HatGuard hatGuard;
    private LifecycleListener lifecycleListener;

    public WardrobeModule(ShardedCore plugin) {
        super(plugin, "wardrobe");
    }

    @Override
    protected void onEnable() {
        hatKey = new NamespacedKey(plugin, "wardrobe_hat");
        try {
            database = new WardrobeDatabase(plugin, moduleFolder());
        } catch (Exception e) {
            throw new IllegalStateException("Could not open wardrobe database", e);
        }
        loadHats();
        hatGuard = new HatGuard();
        lifecycleListener = new LifecycleListener();
        registerListener(lifecycleListener);
        registerCommand("wardrobe", this);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            syncWearerState(player);
        }
    }

    @Override
    protected void onDisable() {
        if (hatGuard != null) HandlerList.unregisterAll(hatGuard);
        hatWearers.clear();
        if (database != null) database.close();
        database = null;
    }

    private void loadHats() {
        hats.clear();
        var section = config.getConfigurationSection("hats");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            var hat = section.getConfigurationSection(id);
            if (hat == null) continue;
            hats.put(id, new HatOption(
                    id,
                    hat.getInt("slot", 0),
                    hat.getString("permission", "sharded.wardrobe." + id),
                    hat.getString("itemsadder-id", "HATS:" + id.toUpperCase(Locale.ROOT)),
                    hat.getString("material", "HATS:" + id.toUpperCase(Locale.ROOT)),
                    hat.getString("display-name", id),
                    hat.getStringList("lore")
            ));
        }
    }

    public boolean isWardrobeHat(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(hatKey, PersistentDataType.STRING);
    }

    /** Called from token shop via [wardrobe_unlock] hat_id */
    public boolean unlock(Player player, String hatId) {
        HatOption hat = hats.get(hatId.toLowerCase(Locale.ROOT));
        if (hat == null) return false;
        if (database != null) database.unlock(player.getUniqueId(), hat.id());
        send(player, "unlocked", "%hat%", hat.displayName());
        return true;
    }

    public boolean owns(Player player, HatOption hat) {
        if (player.hasPermission(hat.permission())) return true;
        return database != null && database.isUnlocked(player.getUniqueId(), hat.id());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.wardrobe.use")) {
            send(player, "no-permission");
            return true;
        }
        openMenu(player);
        return true;
    }

    public void openMenu(Player player) {
        int rows = config.getInt("menu-rows", 3);
        MenuHolder menuHolder = new MenuHolder();
        Inventory inventory = plugin.getServer().createInventory(menuHolder, rows * 9, Text.c(MENU_TITLE));
        TrackedInventories.track(inventory, menuHolder);
        Material fillerMat = Material.matchMaterial(config.getString("filler-material", "BLACK_STAINED_GLASS_PANE"));
        if (fillerMat == null) fillerMat = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack filler = new ItemBuilder(fillerMat).name(" ").hideAll().build();
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler.clone());

        Map<String, String> ph = equipPlaceholders(player);
        for (HatOption hat : hats.values()) {
            ItemStack stack = resolveDisplayItem(hat);
            inventory.setItem(hat.slot(), new ItemBuilder(stack)
                    .name(apply(hat.displayName(), ph))
                    .lore(apply(hat.lore(), ph))
                    .hideAll()
                    .build());
        }

        int removeSlot = config.getInt("remove.slot", 4);
        inventory.setItem(removeSlot, new ItemBuilder(Material.BARRIER)
                .name(config.getString("remove.display-name", "&c&lREMOVE HAT"))
                .lore(config.getStringList("remove.lore"))
                .hideAll()
                .build());

        player.openInventory(inventory);
    }

    public void handleMenuClick(Player player, int slot) {
        if (slot == config.getInt("remove.slot", 4)) {
            player.closeInventory();
            unequip(player);
            send(player, "removed");
            return;
        }

        for (HatOption hat : hats.values()) {
            if (hat.slot() != slot) continue;
            if (!owns(player, hat)) {
                player.closeInventory();
                send(player, "not-owned", "%hat%", hat.displayName());
                return;
            }
            player.closeInventory();
            equip(player, hat);
            send(player, "equipped", "%hat%", hat.displayName());
            return;
        }
    }

    public Map<String, String> equipPlaceholders(Player player) {
        Map<String, String> map = new LinkedHashMap<>();
        String yes = config.getString("placeholders.owned-yes", "&#9FFF00&nYes");
        String no = config.getString("placeholders.owned-no", "&#FF2727&nNo");
        for (HatOption hat : hats.values()) {
            map.put("wardrobe_owned_" + hat.id(), owns(player, hat) ? yes : no);
        }
        String equipped = database == null ? "" : database.getEquipped(player.getUniqueId());
        map.put("equipped_hat", equipped == null || equipped.isBlank()
                ? config.getString("placeholders.none", "&7None") : equipped);
        return map;
    }

    private void trackWearer(UUID uuid) {
        if (!hatWearers.add(uuid)) return;
        if (hatWearers.size() == 1) registerListener(hatGuard);
    }

    private void untrackWearer(UUID uuid) {
        if (!hatWearers.remove(uuid)) return;
        if (hatWearers.isEmpty()) HandlerList.unregisterAll(hatGuard);
    }

    private void syncWearerState(Player player) {
        if (isWardrobeHat(player.getInventory().getHelmet())) {
            trackWearer(player.getUniqueId());
            return;
        }
        if (database == null) return;
        String equipped = database.getEquipped(player.getUniqueId());
        if (equipped != null && !equipped.isBlank()) {
            scheduleReequip(player);
        }
    }

    private void scheduleReequip(Player player) {
        if (database == null) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> reequipIfNeeded(player), 5L);
    }

    private void reequipIfNeeded(Player player) {
        if (!player.isOnline()) return;
        String equipped = database.getEquipped(player.getUniqueId());
        if (equipped == null || equipped.isBlank()) return;
        HatOption hat = hats.get(equipped);
        if (hat == null || !owns(player, hat)) return;
        ItemStack current = player.getInventory().getHelmet();
        if (isWardrobeHat(current)) {
            trackWearer(player.getUniqueId());
            return;
        }
        equipSilent(player, hat);
    }

    public void equip(Player player, HatOption hat) {
        equipSilent(player, hat);
        if (database != null) database.setEquipped(player.getUniqueId(), hat.id());
    }

    private void equipSilent(Player player, HatOption hat) {
        ItemStack hatItem = buildHatItem(hat);
        if (hatItem == null) {
            send(player, "item-missing", "%hat%", hat.displayName());
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack current = inv.getHelmet();
        if (isWardrobeHat(current)) {
            inv.setHelmet(null);
        } else if (current != null && !current.getType().isAir()) {
            Map<Integer, ItemStack> leftover = inv.addItem(current.clone());
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        inv.setHelmet(hatItem);
        trackWearer(player.getUniqueId());
    }

    private void unequip(Player player) {
        PlayerInventory inv = player.getInventory();
        if (isWardrobeHat(inv.getHelmet())) {
            inv.setHelmet(null);
        }
        removeWardrobeHatsFromInventory(player);
        untrackWearer(player.getUniqueId());
        if (database != null) database.setEquipped(player.getUniqueId(), "");
    }

    private void removeWardrobeHatsFromInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isWardrobeHat(inv.getItem(i))) inv.setItem(i, null);
        }
        ItemStack off = inv.getItemInOffHand();
        if (isWardrobeHat(off)) inv.setItemInOffHand(null);
    }

    private ItemStack resolveDisplayItem(HatOption hat) {
        ItemStack stack = ItemsAdderHook.resolve(hat.itemsadderId());
        if (stack == null) stack = ItemsAdderHook.resolve(hat.material());
        if (stack == null) stack = ItemsAdderHook.parseItem("itemsadder-" + hat.itemsadderId());
        if (stack != null) return stack.clone();
        Material mat = Material.matchMaterial(hat.material());
        if (mat == null) mat = Material.PAPER;
        return new ItemStack(mat);
    }

    private ItemStack buildHatItem(HatOption hat) {
        ItemStack stack = resolveDisplayItem(hat);
        if (stack == null || stack.getType().isAir()) return null;
        stack = stack.clone();
        int prot = config.getInt("enchantments.protection", 4);
        int unb = config.getInt("enchantments.unbreaking", 3);
        double armor = config.getDouble("attributes.armor", 3.0);
        double toughness = config.getDouble("attributes.armor-toughness", 0.0);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.PROTECTION, prot, true);
            meta.addEnchant(Enchantment.UNBREAKING, unb, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.setUnbreakable(false);
            NamespacedKey armorKey = new NamespacedKey(plugin, "wardrobe_armor_" + hat.id());
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                    armorKey, armor, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
            if (toughness > 0) {
                NamespacedKey toughKey = new NamespacedKey(plugin, "wardrobe_toughness_" + hat.id());
                meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                        toughKey, toughness, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(hatKey, PersistentDataType.STRING, hat.id());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<String> apply(List<String> lines, Map<String, String> ph) {
        List<String> out = new ArrayList<>();
        for (String line : lines) out.add(apply(line, ph));
        return out;
    }

    private String apply(String line, Map<String, String> ph) {
        String out = line;
        for (Map.Entry<String, String> e : ph.entrySet()) out = out.replace("%" + e.getKey() + "%", e.getValue());
        return out;
    }

    private record HatOption(String id, int slot, String permission, String itemsadderId,
                             String material, String displayName, List<String> lore) {
    }

    /** Only registered while at least one player wears a wardrobe hat. */
    private final class HatGuard implements Listener {

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onDrop(PlayerDropItemEvent event) {
            if (isWardrobeHat(event.getItemDrop().getItemStack())) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onHatMove(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (!hatWearers.contains(player.getUniqueId())) return;
            if (isWardrobeHat(event.getCurrentItem()) || isWardrobeHat(event.getCursor())) {
                event.setCancelled(true);
                return;
            }
            if (event.getClick() == ClickType.NUMBER_KEY) {
                ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
                if (isWardrobeHat(hotbar)) event.setCancelled(true);
                return;
            }
            if (event.getSlotType() == InventoryType.SlotType.ARMOR && event.getRawSlot() == 39) {
                if (isWardrobeHat(player.getInventory().getHelmet())) event.setCancelled(true);
                return;
            }
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                if (isWardrobeHat(event.getCurrentItem())) event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onHatDrag(InventoryDragEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (!hatWearers.contains(player.getUniqueId())) return;
            for (ItemStack stack : event.getNewItems().values()) {
                if (isWardrobeHat(stack)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (isWardrobeHat(event.getOldCursor())) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onDeath(PlayerDeathEvent event) {
            Player player = event.getEntity();
            if (!hatWearers.contains(player.getUniqueId())) return;
            event.getDrops().removeIf(WardrobeModule.this::isWardrobeHat);
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                if (isWardrobeHat(inv.getItem(i))) inv.setItem(i, null);
            }
            if (isWardrobeHat(inv.getHelmet())) inv.setHelmet(null);
            ItemStack off = inv.getItemInOffHand();
            if (isWardrobeHat(off)) inv.setItemInOffHand(null);
            untrackWearer(player.getUniqueId());
        }
    }

    /** Join/respawn only — not on hot inventory paths. */
    private final class LifecycleListener implements Listener {

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> syncWearerState(event.getPlayer()), 1L);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onRespawn(PlayerRespawnEvent event) {
            scheduleReequip(event.getPlayer());
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            untrackWearer(event.getPlayer().getUniqueId());
        }
    }
}
