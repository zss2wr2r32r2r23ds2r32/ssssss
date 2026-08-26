package com.shardedcore.gui;

import com.shardedcore.ShardedCore;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Items;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** 54-slot cosmetics browser used by /tags, /chatcolor, and /glows. */
public final class CosmeticsMenus {

    public static final int[] AREA = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private CosmeticsMenus() {
    }

    public static void open(ShardedCore plugin, Player player, FileConfiguration config, String title,
                            List<Entry> entries, int page, String kind, boolean limitedPage,
                            Consumer<Integer> openPage, Runnable limitedClick, Consumer<Entry> select) {
        int per = AREA.length;
        int pages = Math.max(1, (entries.size() + per - 1) / per);
        int current = Math.max(0, Math.min(page, pages - 1));
        Menus.Menu menu = plugin.menus().create(player, title, config.getInt("gui.rows", 6));
        int start = current * per;
        Material icon = Sounds.material(config.getString("gui.item-material", "NAME_TAG"), Material.NAME_TAG);
        for (int i = 0; i < per && start + i < entries.size(); i++) {
            Entry entry = entries.get(start + i);
            List<String> lore = lore(config, entry, kind);
            String name = Text.apply(config.getString("gui.item-name", "%color%&l%name%"),
                    "color", entry.color(), "name", entry.title(), "kind", kind);
            menu.set(AREA[i], Items.named(icon, name, lore), event -> {
                event.setCancelled(true);
                select.accept(entry);
            });
        }
        ItemStack glass = Items.named(
                Sounds.material(config.getString("gui.filler.material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE),
                config.getString("gui.filler.name", " "),
                config.getStringList("gui.filler.lore"));
        frame(menu, glass);
        ConfigurationSection prev = config.getConfigurationSection("gui.previous");
        ConfigurationSection next = config.getConfigurationSection("gui.next");
        ConfigurationSection extra = config.getConfigurationSection("gui.extra");
        if (current > 0 && prev != null) {
            menu.set(prev.getInt("slot", 48), Items.fromSection(prev, player, "page", String.valueOf(current)), event -> {
                event.setCancelled(true);
                openPage.accept(current - 1);
            });
        }
        if (current + 1 < pages && next != null) {
            menu.set(next.getInt("slot", 50), Items.fromSection(next, player, "page", String.valueOf(current + 2)), event -> {
                event.setCancelled(true);
                openPage.accept(current + 1);
            });
        }
        if (extra != null && extra.getBoolean("enabled", true) && limitedClick != null && !limitedPage) {
            menu.set(extra.getInt("slot", 49), Items.fromSection(extra, player), event -> {
                event.setCancelled(true);
                limitedClick.run();
            });
        } else if (extra != null && extra.getBoolean("close-when-hidden", false)) {
            menu.set(extra.getInt("slot", 49), Items.fromSection(extra, player), event -> {
                event.setCancelled(true);
                player.closeInventory();
            });
        }
        menu.fill(glass);
        plugin.menus().open(player, menu);
    }

    public static List<String> lore(FileConfiguration config, Entry entry, String kind) {
        String owned = config.getString("gui.status-owned", "%color% ☀︎ &fStatus: &#94FF00&nOWNED");
        String locked = config.getString("gui.status-locked", "%color% ☀︎ &fStatus: &#94FF00&nUNLOCK");
        List<String> template = config.getStringList("gui.lore");
        if (template.isEmpty()) {
            template = List.of(
                    "%color%&l%name%",
                    "&8Description",
                    "",
                    "%color%Information:",
                    "%color%| &fClick to",
                    "%color%| &fEquip %color%%display%",
                    "",
                    "%status%",
                    "",
                    "%click%"
            );
        }
        String click = config.getString("gui.click-footer",
                "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Equip");
        return Text.applyList(new ArrayList<>(template),
                "color", entry.color(),
                "name", entry.title(),
                "display", entry.display(),
                "kind", kind,
                "description", entry.description(),
                "status", Text.apply(entry.owned() ? owned : locked, "color", entry.color(), "name", entry.title()),
                "click", click);
    }

    public static String colorOf(String text, String fallback) {
        return ColorUtil.colorCode(ColorUtil.firstHex(text, fallback));
    }

    public static String pretty(String id) {
        if (id == null || id.isBlank()) return "Example";
        String stripped = ColorUtil.strip(id).replace('_', ' ').trim();
        if (stripped.isEmpty()) return "Example";
        return stripped.substring(0, 1).toUpperCase(Locale.ROOT) + stripped.substring(1);
    }

    private static void frame(Menus.Menu menu, ItemStack glass) {
        int size = menu.inventory().getSize();
        int rows = size / 9;
        Set<Integer> inner = new HashSet<>();
        for (int slot : AREA) inner.add(slot);
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8 || !inner.contains(i)) {
                if (menu.inventory().getItem(i) == null) menu.set(i, glass);
            }
        }
    }

    public record Entry(String id, String title, String display, String description, String color, boolean owned) {
    }
}
