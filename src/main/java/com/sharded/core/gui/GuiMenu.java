package com.sharded.core.gui;

import com.sharded.core.util.BundleUtil;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.ItemsAdderHook;
import com.sharded.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** DeluxeMenus-style inventory menu loaded from YAML. */
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

    public record GuiItem(int slot, ItemStack display, String rawName, List<String> rawLore,
                          List<String> leftClickCommands, List<String> clickCommands) {
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

            ItemStack stack = ItemsAdderHook.parseItem(item.getString("material", "STONE"));
            if (stack == null) stack = new ItemStack(org.bukkit.Material.STONE);

            String name = ColorUtil.normalize(item.getString("display_name", " "));
            List<String> lore = new ArrayList<>();
            for (String line : item.getStringList("lore")) lore.add(ColorUtil.normalize(line));

            stack = new ItemBuilder(stack).name(name).lore(lore).hideAll().build();

            List<String> left = item.getStringList("left_click_commands");
            List<String> click = item.getStringList("click_commands");
            if (click.isEmpty()) click = left;
            if (click.isEmpty()) click = item.getStringList("right_click_commands");

            List<Integer> slots = new ArrayList<>();
            if (item.contains("slot")) slots.add(item.getInt("slot"));
            if (item.contains("slots")) slots.addAll(item.getIntegerList("slots"));

            for (int slot : slots) {
                itemsBySlot.put(slot, new GuiItem(slot, stack, name, lore, left, click));
            }
        }
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
        if (!openPermission.isEmpty() && !player.hasPermission(resolvePermission(openPermission))) {
            manager.message(player, manager.noPermissionMessage(), true);
            return;
        }
        OpenGuiHolder holder = new OpenGuiHolder(id);
        Inventory inventory = Bukkit.createInventory(holder, size, Text.c(apply(title, player, extraPlaceholders, manager)));
        holder.inventory = inventory;

        for (GuiItem guiItem : itemsBySlot.values()) {
            ItemStack placed = applyItem(guiItem, player, extraPlaceholders, manager);
            BundleUtil.stripMenuTooltip(placed);
            inventory.setItem(guiItem.slot(), placed);
        }
        player.openInventory(inventory);
        manager.runCommands(player, openCommands, extraPlaceholders);
    }

    private ItemStack applyItem(GuiItem item, Player player, Map<String, String> extra, GuiManager manager) {
        ItemStack copy = item.display().clone();
        var meta = copy.getItemMeta();
        if (meta == null) return copy;
        if (item.rawName() != null && !item.rawName().isBlank()) {
            meta.displayName(Text.c(apply(item.rawName(), player, extra, manager)));
        }
        if (item.rawLore() != null && !item.rawLore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : item.rawLore()) {
                lore.add(Text.c(apply(line, player, extra, manager)));
            }
            meta.lore(lore);
        }
        copy.setItemMeta(meta);
        return copy;
    }

    private Component applyComponent(Component component, Player player, Map<String, String> extra, GuiManager manager) {
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(component);
        return Text.c(apply(legacy, player, extra, manager));
    }

    private static String resolvePermission(String permission) {
        if (permission.startsWith("sharded.")) return permission;
        return "sharded." + permission;
    }

    public static String apply(String input, Player player, Map<String, String> extra, GuiManager manager) {
        if (input == null) return "";
        String out = input.replace("%player_name%", player.getName()).replace("%player%", player.getName());
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                out = out.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
            }
        }
        return manager.applyPlaceholders(player, out);
    }
}
