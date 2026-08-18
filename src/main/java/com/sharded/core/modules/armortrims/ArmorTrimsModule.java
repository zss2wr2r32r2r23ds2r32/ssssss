package com.sharded.core.modules.armortrims;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /armortrims - a trim station GUI: put your armor piece in the middle,
 * pick a trim pattern on the left, a trim material on the right, then confirm.
 * Netherite (and the netherite upgrade template) are excluded.
 */
public final class ArmorTrimsModule extends Module implements CommandExecutor {

    private static final int ARMOR_SLOT = 22;
    private static final int CONFIRM_SLOT = 49;
    private static final int[] PATTERN_SLOTS = {
            0, 1, 2, 9, 10, 11, 18, 19, 20, 27, 28, 29, 36, 37, 38, 45, 46, 47};
    private static final int[] MATERIAL_SLOTS = {
            6, 7, 8, 15, 16, 17, 24, 25, 26, 33, 34, 35};

    private static final Map<String, Material> MATERIAL_ICONS = Map.ofEntries(
            Map.entry("quartz", Material.QUARTZ),
            Map.entry("iron", Material.IRON_INGOT),
            Map.entry("gold", Material.GOLD_INGOT),
            Map.entry("copper", Material.COPPER_INGOT),
            Map.entry("emerald", Material.EMERALD),
            Map.entry("diamond", Material.DIAMOND),
            Map.entry("redstone", Material.REDSTONE),
            Map.entry("lapis", Material.LAPIS_LAZULI),
            Map.entry("amethyst", Material.AMETHYST_SHARD));

    private final class TrimHolder implements InventoryHolder {
        private Inventory inventory;
        private TrimPattern selectedPattern;
        private TrimMaterial selectedMaterial;
        private final List<TrimPattern> patterns = new ArrayList<>();
        private final List<TrimMaterial> materials = new ArrayList<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public ArmorTrimsModule(ShardedCore plugin) {
        super(plugin, "armortrims");
    }

    @Override
    protected void onEnable() {
        registerCommand("armortrims", this);
    }

    @Override
    protected void onDisable() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof TrimHolder) {
                player.closeInventory();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.armortrims.use")) {
            send(player, "no-permission");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        TrimHolder holder = new TrimHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.c(config.getString("title", "&8Armor Trim Station")));
        holder.inventory = inventory;

        List<String> excludedPatterns = lower(config.getStringList("excluded-patterns"));
        List<String> excludedMaterials = lower(config.getStringList("excluded-materials"));

        Registry<TrimPattern> patternRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);
        for (TrimPattern pattern : patternRegistry) {
            NamespacedKey key = patternRegistry.getKey(pattern);
            if (key == null) continue;
            String name = key.getKey().toLowerCase(Locale.ROOT);
            if (name.contains("netherite") || excludedPatterns.contains(name)) continue;
            if (holder.patterns.size() >= PATTERN_SLOTS.length) break;
            holder.patterns.add(pattern);
        }

        Registry<TrimMaterial> materialRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
        for (TrimMaterial material : materialRegistry) {
            NamespacedKey key = materialRegistry.getKey(material);
            if (key == null) continue;
            String name = key.getKey().toLowerCase(Locale.ROOT);
            if (name.contains("netherite") || excludedMaterials.contains(name)) continue;
            if (holder.materials.size() >= MATERIAL_SLOTS.length) break;
            holder.materials.add(material);
        }

        render(holder);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.6f, 1.2f);
        send(player, "opened");
    }

    private void render(TrimHolder holder) {
        Inventory inv = holder.inventory;
        ItemStack armorItem = inv.getItem(ARMOR_SLOT);
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("&r").build();
        for (int slot = 0; slot < 54; slot++) {
            if (slot == ARMOR_SLOT) continue;
            inv.setItem(slot, filler);
        }

        Registry<TrimPattern> patternRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);
        for (int i = 0; i < holder.patterns.size(); i++) {
            TrimPattern pattern = holder.patterns.get(i);
            NamespacedKey key = patternRegistry.getKey(pattern);
            boolean selected = pattern.equals(holder.selectedPattern);
            inv.setItem(PATTERN_SLOTS[i], new ItemBuilder(patternIcon(key))
                    .edit(meta -> meta.displayName(Component.empty()
                            .append(Text.c(selected ? "&a✔ " : "&e"))
                            .append(pattern.description())))
                    .lore(selected ? raw("lore-selected") : raw("lore-click-pattern"))
                    .glow(selected)
                    .hideAll()
                    .build());
        }

        Registry<TrimMaterial> materialRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
        for (int i = 0; i < holder.materials.size(); i++) {
            TrimMaterial material = holder.materials.get(i);
            NamespacedKey key = materialRegistry.getKey(material);
            boolean selected = material.equals(holder.selectedMaterial);
            inv.setItem(MATERIAL_SLOTS[i], new ItemBuilder(materialIcon(key))
                    .edit(meta -> meta.displayName(Component.empty()
                            .append(Text.c(selected ? "&a✔ " : "&b"))
                            .append(material.description())))
                    .lore(selected ? raw("lore-selected") : raw("lore-click-material"))
                    .glow(selected)
                    .hideAll()
                    .build());
        }

        // Armor slot hint + restore item
        if (armorItem == null || armorItem.getType().isAir()) {
            inv.setItem(ARMOR_SLOT, null);
        } else {
            inv.setItem(ARMOR_SLOT, armorItem);
        }
        int[] frame = {12, 13, 14, 21, 23, 30, 31, 32};
        ItemStack frameItem = new ItemBuilder(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
                .name(raw("armor-slot-hint")).build();
        for (int slot : frame) inv.setItem(slot, frameItem);

        boolean ready = holder.selectedPattern != null && holder.selectedMaterial != null
                && armorItem != null && !armorItem.getType().isAir();
        inv.setItem(CONFIRM_SLOT, new ItemBuilder(ready ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                .name(ready ? raw("confirm-ready") : raw("confirm-not-ready"))
                .lore(raw("confirm-lore"))
                .build());
    }

    private Material patternIcon(NamespacedKey key) {
        if (key != null) {
            Material template = Material.getMaterial(key.getKey().toUpperCase(Locale.ROOT) + "_ARMOR_TRIM_SMITHING_TEMPLATE");
            if (template != null) return template;
        }
        return Material.PAPER;
    }

    private Material materialIcon(NamespacedKey key) {
        if (key == null) return Material.PAPER;
        Material icon = MATERIAL_ICONS.get(key.getKey().toLowerCase(Locale.ROOT));
        if (icon != null) return icon;
        icon = Material.getMaterial(key.getKey().toUpperCase(Locale.ROOT));
        if (icon != null) return icon;
        icon = Material.getMaterial(key.getKey().toUpperCase(Locale.ROOT) + "_INGOT");
        return icon != null ? icon : Material.PAPER;
    }

    private List<String> lower(List<String> input) {
        List<String> out = new ArrayList<>();
        for (String s : input) out.add(s.toLowerCase(Locale.ROOT));
        return out;
    }

    private boolean isTrimmable(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getItemMeta() instanceof ArmorMeta;
    }

    /* ----------------------------- events ----------------------------- */

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TrimHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Clicks in the player's own inventory: allow, but handle shift-click into the GUI.
        if (event.getClickedInventory() != null && event.getClickedInventory() != holder.inventory) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack current = event.getCurrentItem();
                if (isTrimmable(current) && (holder.inventory.getItem(ARMOR_SLOT) == null)) {
                    holder.inventory.setItem(ARMOR_SLOT, current.clone());
                    event.getClickedInventory().setItem(event.getSlot(), null);
                    render(holder);
                }
            }
            return;
        }

        int slot = event.getSlot();
        if (slot == ARMOR_SLOT) {
            // Allow placing/taking armor by hand, but only armor pieces.
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !isTrimmable(cursor)) {
                event.setCancelled(true);
                send(player, "not-armor");
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> render(holder));
            return;
        }

        event.setCancelled(true);

        for (int i = 0; i < PATTERN_SLOTS.length; i++) {
            if (PATTERN_SLOTS[i] == slot && i < holder.patterns.size()) {
                holder.selectedPattern = holder.patterns.get(i);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
                render(holder);
                return;
            }
        }
        for (int i = 0; i < MATERIAL_SLOTS.length; i++) {
            if (MATERIAL_SLOTS[i] == slot && i < holder.materials.size()) {
                holder.selectedMaterial = holder.materials.get(i);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.7f);
                render(holder);
                return;
            }
        }
        if (slot == CONFIRM_SLOT) {
            applyTrim(player, holder);
        }
    }

    private void applyTrim(Player player, TrimHolder holder) {
        ItemStack armor = holder.inventory.getItem(ARMOR_SLOT);
        if (!isTrimmable(armor)) {
            send(player, "no-armor");
            return;
        }
        if (holder.selectedPattern == null || holder.selectedMaterial == null) {
            send(player, "no-selection");
            return;
        }
        ArmorMeta meta = (ArmorMeta) armor.getItemMeta();
        meta.setTrim(new ArmorTrim(holder.selectedMaterial, holder.selectedPattern));
        armor.setItemMeta(meta);
        holder.inventory.setItem(ARMOR_SLOT, armor);
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
        send(player, "trim-applied");
        render(holder);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TrimHolder)) return;
        for (int slot : event.getRawSlots()) {
            if (slot < 54 && slot != ARMOR_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TrimHolder)) return;
        ItemStack armor = event.getInventory().getItem(ARMOR_SLOT);
        if (armor != null && !armor.getType().isAir() && event.getPlayer() instanceof Player player) {
            player.getInventory().addItem(armor).values()
                    .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
    }
}
