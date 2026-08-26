package com.shardedcore.modules.settings;

import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.GuiUtil;
import com.shardedcore.util.Text;
import com.shardedcore.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

final class SettingsGuiHandler {

    static final class SettingsGuiHolder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final SettingsModule module;

    SettingsGuiHandler(SettingsModule module) {
        this.module = module;
    }

    void open(Player player) {
        SettingsGuiHolder holder = new SettingsGuiHolder();
        int size = module.config().getInt("gui.size", 36);
        Inventory inv = Bukkit.createInventory(holder, size,
                ColorUtil.parse(Text.apply(module.config().getString("gui.title", "&8Settings"),
                        "%player%", player.getName())));
        holder.inventory = inv;
        fill(inv);

        ConfigurationSection items = module.config().getConfigurationSection("gui.items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) continue;
                if (item.getBoolean("close", false)) {
                    inv.setItem(item.getInt("slot", 31), closeItem(item));
                    continue;
                }
                String toggleKey = item.getString("toggle-key");
                if (toggleKey == null) continue;
                if (!player.hasPermission(item.getString("permission", "shardedcore.settings.use"))) {
                    continue;
                }
                Map<String, String> placeholders = module.placeholders(player);
                inv.setItem(item.getInt("slot", 0), toggleItem(key, item, placeholders));
            }
        }

        TrackedInventories.track(inv, holder);
        player.openInventory(inv);
    }

    void handleClick(Player player, int slot) {
        ConfigurationSection items = module.config().getConfigurationSection("gui.items");
        if (items == null) return;

        for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item == null) continue;
            if (item.getInt("slot", -1) != slot) continue;

            if (item.getBoolean("close", false)) {
                player.closeInventory();
                return;
            }

            String toggleKey = item.getString("toggle-key");
            if (toggleKey == null) return;
            if (!player.hasPermission(item.getString("permission", "shardedcore.settings.use"))) {
                module.sendMessage(player, "no-permission");
                return;
            }

            if (SettingsModule.KEY_PAY.equals(toggleKey)) {
                boolean disabled = module.togglePay(player);
                module.sendMessage(player, disabled ? item.getString("off-message", "pay-off")
                        : item.getString("on-message", "pay-on"));
            } else {
                boolean defaultValue = item.getBoolean("default", true);
                boolean enabled = module.toggleSetting(player, toggleKey, defaultValue, item.getString("effect"));
                module.sendMessage(player, enabled ? item.getString("on-message", "toggle-on")
                        : item.getString("off-message", "toggle-off"));
            }
            open(player);
            return;
        }
    }

    private ItemStack toggleItem(String key, ConfigurationSection item, Map<String, String> placeholders) {
        Material material = Material.matchMaterial(item.getString("material", "PAPER"));
        if (material == null) material = Material.PAPER;
        String name = apply(item.getString("name", key), placeholders);
        List<String> lore = item.getStringList("lore").stream()
                .map(line -> apply(line, placeholders))
                .toList();
        return GuiUtil.item(material, name, lore);
    }

    private ItemStack closeItem(ConfigurationSection item) {
        Material material = Material.matchMaterial(item.getString("material", "BARRIER"));
        if (material == null) material = Material.BARRIER;
        return GuiUtil.item(material, item.getString("name", "&#FF0000&lCLOSE"), item.getStringList("lore"));
    }

    private void fill(Inventory inv) {
        ItemStack pane = GuiUtil.filler(module.config().getConfigurationSection("gui.filler"));
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    private String apply(String input, Map<String, String> placeholders) {
        if (input == null) return "";
        String out = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
        }
        return out;
    }
}
