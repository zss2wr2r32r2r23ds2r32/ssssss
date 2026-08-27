package com.shardedcore.gui;

import com.shardedcore.ShardedCore;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Configs;
import com.shardedcore.util.Items;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Shared cancel / back / confirm / page buttons loaded from gui.yml. */
public final class GuiButtons {

    private static FileConfiguration gui;

    private GuiButtons() {
    }

    public static void load(ShardedCore plugin) {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        Configs.saveDefault(plugin, "gui.yml", file);
        gui = Configs.load(file);
    }

    public static FileConfiguration config() {
        return gui;
    }

    public static String title(String guide, String name) {
        String format = value("title-format", "%name%");
        String resolved = Text.apply(format, "guide", guide == null ? "" : guide, "name", name == null ? "" : name);
        if (resolved == null || resolved.isBlank()) return name == null || name.isBlank() ? (guide == null ? "" : guide) : name;
        return resolved;
    }

    public static ItemStack filler() {
        return item("filler", null);
    }

    public static ItemStack cancel(Player player, String... pairs) {
        return item("cancel", player, pairs);
    }

    public static ItemStack close(Player player, String... pairs) {
        return item("close", player, pairs);
    }

    public static ItemStack back(Player player, String... pairs) {
        return item("back", player, pairs);
    }

    public static ItemStack confirm(Player player, String... pairs) {
        return item("confirm", player, pairs);
    }

    public static ItemStack previous(Player player, String... pairs) {
        return item("previous", player, pairs);
    }

    public static ItemStack next(Player player, String... pairs) {
        return item("next", player, pairs);
    }

    public static int slot(String key, int fallback) {
        if (gui == null) return fallback;
        return gui.getInt(key + ".slot", fallback);
    }

    public static void border(Menus.Menu menu) {
        ItemStack fill = filler();
        int size = menu.inventory().getSize();
        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                if (menu.inventory().getItem(i) == null) menu.set(i, fill);
            }
        }
    }

    public static void place(Menus.Menu menu, String key, int fallbackSlot, ItemStack item,
                             Consumer<InventoryClickEvent> click) {
        if (item == null) return;
        menu.set(slot(key, fallbackSlot), item, click);
    }

    public static void play(Player player, String key) {
        if (player == null || gui == null) return;
        ConfigurationSection section = gui.getConfigurationSection("sounds." + key);
        if (section != null) {
            Sounds.play(player, section);
            return;
        }
        Sounds.play(player, gui.getString("sounds." + key, ""), 1f, 1f);
    }

    public static ItemStack item(String key, Player player, String... pairs) {
        if (gui == null) return Items.named(Material.STONE, key, List.of());
        ConfigurationSection section = gui.getConfigurationSection(key);
        if (section == null) return Items.named(Material.STONE, key, List.of());
        return Items.fromSection(section, player, pairs);
    }

    public static ItemStack coloredBundle(String hex, String name, List<String> lore) {
        return Items.named(bundleMaterial(hex), name, lore);
    }

    public static void fill(Menus.Menu menu) {
        menu.fill(filler());
    }

    public static void glass(Menus.Menu menu, boolean borderOnly) {
        if (borderOnly) border(menu);
        else fill(menu);
    }

    public static void placeBack(Menus.Menu menu, Player player, int slot, Runnable back) {
        if (menu == null) return;
        int size = menu.inventory().getSize();
        int resolved = slot;
        if (resolved < 0 || resolved >= size) resolved = Math.max(0, ((size / 9) - 1) * 9 + 4);
        menu.set(resolved, back(player), event -> {
            event.setCancelled(true);
            play(player, "click");
            if (back != null) back.run();
        });
    }

    public static Material bundleMaterial(String hex) {
        String value = ColorUtil.hex(hex);
        if (value.isEmpty()) return Material.BUNDLE;
        int rgb;
        try {
            rgb = Integer.parseInt(value, 16);
        } catch (NumberFormatException ex) {
            return Material.BUNDLE;
        }
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        String nearest = nearest(r, g, b, hsb[0], hsb[1], hsb[2]);
        Material matched = Material.matchMaterial(nearest);
        return matched == null ? Material.BUNDLE : matched;
    }

    private static String nearest(int r, int g, int b, float hue, float sat, float bright) {
        if (sat < 0.22f) {
            if (bright < 0.18f) return "BLACK_BUNDLE";
            if (bright < 0.45f) return "GRAY_BUNDLE";
            if (bright < 0.75f) return "LIGHT_GRAY_BUNDLE";
            return "BUNDLE";
        }
        float deg = hue * 360f;
        if (bright < 0.28f) return "BLACK_BUNDLE";
        if (deg < 18 || deg >= 345) return "RED_BUNDLE";
        if (deg < 40) return "ORANGE_BUNDLE";
        if (deg < 70) return "YELLOW_BUNDLE";
        if (deg < 100) return "LIME_BUNDLE";
        if (deg < 150) return "GREEN_BUNDLE";
        if (deg < 175) return "CYAN_BUNDLE";
        if (deg < 205) return "LIGHT_BLUE_BUNDLE";
        if (deg < 245) return "BLUE_BUNDLE";
        if (deg < 285) return "PURPLE_BUNDLE";
        if (deg < 320) return "MAGENTA_BUNDLE";
        return "PINK_BUNDLE";
    }

    private static String value(String path, String fallback) {
        if (gui == null) return fallback;
        String text = gui.getString(path, fallback);
        return text == null || text.isBlank() ? fallback : text;
    }

    public static int[] inner(int rows) {
        int safe = Math.max(3, Math.min(6, rows));
        int count = Math.max(1, safe - 2) * 7;
        int[] slots = new int[count];
        int index = 0;
        for (int row = 1; row < safe - 1; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[index++] = row * 9 + col;
            }
        }
        return slots;
    }

    public static String pretty(String id) {
        if (id == null || id.isBlank()) return "Example";
        String stripped = ColorUtil.strip(id).replace('_', ' ').trim();
        if (stripped.isEmpty()) return "Example";
        return stripped.substring(0, 1).toUpperCase(Locale.ROOT) + stripped.substring(1);
    }
}
