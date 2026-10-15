package com.shardedcore.util;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConfigGui {

    public static final class GuiHolder implements InventoryHolder {
        private final String menuId;
        private Inventory inventory;

        public GuiHolder(String menuId) {
            this.menuId = menuId;
        }

        public String menuId() {
            return menuId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private ConfigGui() {
    }

    public static Inventory open(Player player, ConfigurationSection menu, String menuId, Map<String, String> placeholders) {
        int size = Math.max(9, Math.min(54, menu.getInt("size", 27)));
        String title = apply(menu.getString("title", menuId), placeholders);
        GuiHolder holder = new GuiHolder(menuId);
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtil.parse(title));
        holder.inventory = inventory;
        ItemStack filler = GuiUtil.filler(menu.getConfigurationSection("filler"));
        for (int i = 0; i < size; i++) inventory.setItem(i, filler);

        ConfigurationSection items = menu.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection itemSection = items.getConfigurationSection(key);
                if (itemSection == null) continue;
                int slot = itemSection.getInt("slot", -1);
                if (slot < 0 || slot >= size) continue;
                String name = apply(itemSection.getString("name", key), placeholders);
                List<String> lore = new ArrayList<>();
                for (String line : itemSection.getStringList("lore")) lore.add(apply(line, placeholders));
                org.bukkit.Material material = org.bukkit.Material.matchMaterial(itemSection.getString("material", "PAPER"));
                if (material == null) material = org.bukkit.Material.PAPER;
                inventory.setItem(slot, GuiUtil.item(material, name, lore));
            }
        }
        TrackedInventories.track(inventory, holder);
        player.openInventory(inventory);
        return inventory;
    }

    public static String action(ConfigurationSection menu, int slot) {
        ConfigurationSection items = menu.getConfigurationSection("items");
        if (items == null) return null;
        for (String key : items.getKeys(false)) {
            ConfigurationSection itemSection = items.getConfigurationSection(key);
            if (itemSection != null && itemSection.getInt("slot", -1) == slot) {
                return itemSection.getString("action");
            }
        }
        return null;
    }

    private static String apply(String input, Map<String, String> placeholders) {
        if (input == null) return "";
        String out = input;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                out = out.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return out;
    }
}
