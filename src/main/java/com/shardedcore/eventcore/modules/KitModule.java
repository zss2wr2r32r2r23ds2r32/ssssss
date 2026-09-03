package com.shardedcore.eventcore.modules;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.config.ConfigFile;
import com.shardedcore.eventcore.module.EventModule;
import com.shardedcore.eventcore.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stores and hands out kits.
 *
 * <p>Kits are captured from an admin's own inventory with {@code /kit create},
 * then serialised to Base64 in {@code kits.yml}. Decoded stacks are cached in
 * memory, so giving a kit to a full server is a set of array copies rather than
 * hundreds of deserialisation passes.</p>
 */
public final class KitModule extends EventModule {

    /** A decoded kit, ready to be stamped onto an inventory. */
    private record Kit(String name, ItemStack[] storage, ItemStack[] armor, ItemStack offhand) {

        Kit copy() {
            return new Kit(name, cloneAll(storage), cloneAll(armor), offhand == null ? null : offhand.clone());
        }

        private static ItemStack[] cloneAll(ItemStack[] source) {
            ItemStack[] out = new ItemStack[source.length];
            for (int index = 0; index < source.length; index++) {
                out[index] = source[index] == null ? null : source[index].clone();
            }
            return out;
        }
    }

    private static final int STORAGE_SIZE = 36;
    private static final int ARMOR_SIZE = 4;

    private ConfigFile kitsFile;
    private final Map<String, Kit> cache = new HashMap<>();
    private List<Material> offhandPriority = List.of();

    public KitModule(ShardedEventCore plugin) {
        super(plugin, "kits", "Kit storage, /kit create and auto-equipping.");
    }

    @Override
    protected boolean hasListeners() {
        return false;
    }

    @Override
    protected void onModuleEnable() {
        kitsFile = new ConfigFile(plugin, "kits.yml");
        loadAll();
    }

    @Override
    protected void onConfigReload() {
        if (kitsFile != null) {
            kitsFile.reload();
        }
        loadAll();
    }

    private void loadAll() {
        cache.clear();
        offhandPriority = readOffhandPriority();
        if (kitsFile == null) {
            return;
        }
        ConfigurationSection root = kitsFile.raw().getConfigurationSection("kits");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            cache.put(key.toLowerCase(Locale.ROOT), new Kit(
                    key,
                    Items.decodeAll(section.getStringList("storage"), STORAGE_SIZE),
                    Items.decodeAll(section.getStringList("armor"), ARMOR_SIZE),
                    Items.decode(section.getString("offhand"))));
        }
    }

    /** Ordered, because the first match in the list wins the offhand slot. */
    private List<Material> readOffhandPriority() {
        List<Material> out = new ArrayList<>(4);
        for (String raw : config().raw().getStringList("auto-offhand-priority")) {
            Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
            if (material != null && !material.isAir() && !out.contains(material)) {
                out.add(material);
            }
        }
        if (out.isEmpty()) {
            out.add(Material.TOTEM_OF_UNDYING);
            out.add(Material.SHIELD);
        }
        return out;
    }

    // ------------------------------------------------------------------ query

    public boolean exists(String name) {
        return name != null && cache.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public List<String> names() {
        return new ArrayList<>(cache.keySet());
    }

    /** Kit names offered by tab completion even before they exist. */
    public List<String> suggestedNames() {
        List<String> out = new ArrayList<>(config().raw().getStringList("known-kits"));
        for (String existing : cache.keySet()) {
            if (!out.contains(existing)) {
                out.add(existing);
            }
        }
        return out;
    }

    // ----------------------------------------------------------------- create

    /**
     * Captures {@code player}'s inventory, armour and offhand as a named kit.
     *
     * <p>The captured stacks are deep-copied. Bukkit hands back live mirrors of
     * the underlying inventory, so keeping those references would let a later
     * {@code /clear} — which zeroes stacks in place — empty the cached kit.</p>
     */
    public void create(String name, Player player) {
        String key = name.toLowerCase(Locale.ROOT);
        PlayerInventory inventory = player.getInventory();

        ItemStack[] storage = snapshot(inventory.getStorageContents(), STORAGE_SIZE);
        ItemStack[] armor = snapshot(inventory.getArmorContents(), ARMOR_SIZE);
        ItemStack offhand = Items.isEmpty(inventory.getItemInOffHand())
                ? null : inventory.getItemInOffHand().clone();

        ConfigurationSection section = kitsFile.section("kits." + key);
        section.set("storage", Items.encodeAll(storage));
        section.set("armor", Items.encodeAll(armor));
        section.set("offhand", Items.encode(offhand));
        kitsFile.save();

        cache.put(key, new Kit(name, storage, armor, offhand));
    }

    /** Copies a live inventory array into a detached, fixed-size array. */
    private static ItemStack[] snapshot(ItemStack[] source, int size) {
        ItemStack[] out = new ItemStack[size];
        int limit = Math.min(size, source.length);
        for (int index = 0; index < limit; index++) {
            out[index] = Items.isEmpty(source[index]) ? null : source[index].clone();
        }
        return out;
    }

    public boolean delete(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (cache.remove(key) == null) {
            return false;
        }
        kitsFile.raw().set("kits." + key, null);
        kitsFile.save();
        return true;
    }

    // -------------------------------------------------------------------- give

    public boolean give(String name, Player player) {
        Kit kit = cache.get(name == null ? "" : name.toLowerCase(Locale.ROOT));
        if (kit == null) {
            return false;
        }
        apply(kit.copy(), player);
        return true;
    }

    /** Gives a kit to everyone in {@code players}; returns how many were served. */
    public int giveAll(String name, Collection<? extends Player> players) {
        Kit kit = cache.get(name == null ? "" : name.toLowerCase(Locale.ROOT));
        if (kit == null) {
            return -1;
        }
        int served = 0;
        for (Player player : players) {
            if (config().raw().getBoolean("skip-spectators", true)
                    && player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            apply(kit.copy(), player);
            served++;
        }
        return served;
    }

    public int giveEveryone(String name) {
        return giveAll(name, Bukkit.getOnlinePlayers());
    }

    private void apply(Kit kit, Player player) {
        PlayerInventory inventory = player.getInventory();
        if (config().raw().getBoolean("clear-inventory-first", true)) {
            inventory.clear();
        }
        inventory.setStorageContents(kit.storage());
        inventory.setArmorContents(kit.armor());
        if (kit.offhand() != null) {
            inventory.setItemInOffHand(kit.offhand());
        }

        if (config().raw().getBoolean("auto-equip", true)) {
            autoEquipArmor(inventory);
        }
        if (config().raw().getBoolean("auto-offhand", true)) {
            autoOffhand(inventory);
        }
        if (config().raw().getBoolean("select-hotbar-slot", true)) {
            inventory.setHeldItemSlot(Math.max(0, Math.min(8, config().raw().getInt("hotbar-slot", 0))));
        }
        if (config().raw().getBoolean("heal-on-give", true)) {
            double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                    ? 20.0D : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            player.setHealth(max);
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
            player.setFireTicks(0);
        }
        player.updateInventory();
    }

    /**
     * Moves any armour still sitting in the main inventory into a free armour
     * slot, so a kit built in the hotbar still ends up worn.
     */
    private void autoEquipArmor(PlayerInventory inventory) {
        ItemStack[] armor = inventory.getArmorContents();
        boolean changed = false;
        ItemStack[] storage = inventory.getStorageContents();

        for (int index = 0; index < storage.length; index++) {
            ItemStack stack = storage[index];
            if (Items.isEmpty(stack)) {
                continue;
            }
            EquipmentSlot slot = Items.armorSlot(stack.getType());
            if (slot == null) {
                continue;
            }
            int armorIndex = Items.armorIndex(slot);
            if (armorIndex < 0 || armorIndex >= armor.length || !Items.isEmpty(armor[armorIndex])) {
                continue;
            }
            armor[armorIndex] = stack;
            storage[index] = null;
            changed = true;
        }

        if (changed) {
            inventory.setArmorContents(armor);
            inventory.setStorageContents(storage);
        }
    }

    /** Promotes the highest priority offhand item (totem, then shield) if the slot is free. */
    private void autoOffhand(PlayerInventory inventory) {
        if (!Items.isEmpty(inventory.getItemInOffHand())) {
            return;
        }
        ItemStack[] storage = inventory.getStorageContents();
        for (Material wanted : offhandPriority) {
            for (int index = 0; index < storage.length; index++) {
                ItemStack stack = storage[index];
                if (!Items.isEmpty(stack) && stack.getType() == wanted) {
                    inventory.setItemInOffHand(stack);
                    storage[index] = null;
                    inventory.setStorageContents(storage);
                    return;
                }
            }
        }
    }
}
