package com.sharded.core.gui;

import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A DeluxeMenus-style inventory menu loaded from YAML. */
public final class GuiMenu {

    public static final class OpenGuiHolder implements InventoryHolder {
        public final String menuId;
        private Inventory inventory;

        public OpenGuiHolder(String menuId) {
            this.menuId = menuId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public record GuiItem(int slot, ItemStack display, List<String> leftClickCommands, List<String> clickCommands) {
    }

    private final String id;
    private final String title;
    private final int size;
    private final String openPermission;
    private final List<String> openCommands;
    private final Map<Integer, GuiItem> itemsBySlot = new HashMap<>();

    public GuiMenu(String id, YamlConfiguration yaml) {
        this.id = id;
        this.title = yaml.getString("menu_title", id);
        this.size = Math.max(9, Math.min(54, yaml.getInt("size", 27)));
        this.openPermission = yaml.getString("open_permission", "");
        this.openCommands = yaml.getStringList("open_commands");
        loadItems(yaml.getConfigurationSection("items"));
    }

    private void loadItems(ConfigurationSection section) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null) continue;

            Material material = parseMaterial(item.getString("material", "STONE"));
            String name = item.getString("display_name", " ");
            List<String> lore = item.getStringList("lore");
            List<String> left = item.getStringList("left_click_commands");
            List<String> click = item.getStringList("click_commands");
            if (click.isEmpty()) click = left;
            if (click.isEmpty()) click = item.getStringList("right_click_commands");

            ItemStack stack = new ItemBuilder(material).name(name).lore(lore).hideAll().build();

            List<Integer> slots = new ArrayList<>();
            if (item.contains("slot")) slots.add(item.getInt("slot"));
            if (item.contains("slots")) slots.addAll(item.getIntegerList("slots"));

            for (int slot : slots) {
                itemsBySlot.put(slot, new GuiItem(slot, stack, left, click));
            }
        }
    }

    private static Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) return Material.STONE;
        // ItemsAdder/custom ids fall back to paper.
        if (raw.contains(":")) raw = raw.substring(raw.lastIndexOf(':') + 1).toUpperCase();
        Material material = Material.matchMaterial(raw);
        return material == null ? Material.STONE : material;
    }

    public String id() {
        return id;
    }

    public List<String> openCommands() {
        return openCommands;
    }

    public GuiItem itemAt(int slot) {
        return itemsBySlot.get(slot);
    }

    public void open(Player player, GuiManager manager, Map<String, String> extraPlaceholders) {
        if (!openPermission.isEmpty() && !player.hasPermission(openPermission)) {
            manager.message(player, manager.noPermissionMessage());
            return;
        }
        OpenGuiHolder holder = new OpenGuiHolder(id);
        Inventory inventory = Bukkit.createInventory(holder, size, apply(title, player, extraPlaceholders));
        holder.inventory = inventory;

        for (GuiItem guiItem : itemsBySlot.values()) {
            inventory.setItem(guiItem.slot(), applyItem(guiItem.display(), player, extraPlaceholders));
        }
        player.openInventory(inventory);
        manager.runCommands(player, openCommands, extraPlaceholders);
    }

    private ItemStack applyItem(ItemStack template, Player player, Map<String, String> extra) {
        ItemStack copy = template.clone();
        var meta = copy.getItemMeta();
        if (meta == null) return copy;
        if (meta.hasDisplayName()) meta.displayName(applyComponent(meta.displayName(), player, extra));
        if (meta.hasLore()) {
            List<Component> lore = new ArrayList<>();
            for (Component line : meta.lore()) lore.add(applyComponent(line, player, extra));
            meta.lore(lore);
        }
        copy.setItemMeta(meta);
        return copy;
    }

    private Component applyComponent(Component component, Player player, Map<String, String> extra) {
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(component);
        return Text.c(apply(legacy, player, extra));
    }

    public static String apply(String input, Player player, Map<String, String> extra) {
        if (input == null) return "";
        String out = input.replace("%player_name%", player.getName())
                .replace("%player%", player.getName());
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                out = out.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
            }
        }
        return GuiManager.instance().applyPlaceholders(player, out);
    }
}
