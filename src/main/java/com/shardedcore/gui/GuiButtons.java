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
        String format = value("title-format", "☀ %guide% ☀ Previewing | %name%");
        return Text.apply(format, "guide", guide == null ? "" : guide, "name", name == null ? "" : name);
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
        String nearest = nearest(r, g, b);
        Material matched = Material.matchMaterial(nearest);
        return matched == null ? Material.BUNDLE : matched;
    }

    private static String nearest(int r, int g, int b) {
        String[] names = {
                "RED_BUNDLE", "ORANGE_BUNDLE", "YELLOW_BUNDLE", "LIME_BUNDLE", "GREEN_BUNDLE",
                "CYAN_BUNDLE", "LIGHT_BLUE_BUNDLE", "BLUE_BUNDLE", "PURPLE_BUNDLE", "MAGENTA_BUNDLE",
                "PINK_BUNDLE", "BROWN_BUNDLE", "BLACK_BUNDLE", "GRAY_BUNDLE", "LIGHT_GRAY_BUNDLE"
        };
        int[][] colors = {
                {255, 0, 0}, {255, 140, 0}, {255, 248, 0}, {138, 255, 0}, {90, 165, 0},
                {0, 255, 224}, {0, 193, 255}, {0, 59, 255}, {128, 0, 255}, {255, 0, 200},
                {255, 116, 223}, {120, 70, 40}, {26, 26, 26}, {128, 128, 128}, {180, 180, 180}
        };
        int best = 0;
        int distance = Integer.MAX_VALUE;
        for (int i = 0; i < colors.length; i++) {
            int dr = r - colors[i][0];
            int dg = g - colors[i][1];
            int db = b - colors[i][2];
            int d = dr * dr + dg * dg + db * db;
            if (d < distance) {
                distance = d;
                best = i;
            }
        }
        return names[best];
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
