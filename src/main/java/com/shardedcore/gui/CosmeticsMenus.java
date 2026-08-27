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
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Paginated cosmetics browser used by /tags and /chatcolor. Border glass only. */
public final class CosmeticsMenus {

    public static final int[] AREA = GuiButtons.inner(6);

    private CosmeticsMenus() {
    }

    public static void open(ShardedCore plugin, Player player, FileConfiguration config, String title,
                            List<Entry> entries, int page, String kind, boolean limitedPage,
                            Consumer<Integer> openPage, Runnable limitedClick, Consumer<Entry> select) {
        open(plugin, player, config, title, entries, page, kind, limitedPage, openPage, limitedClick, select, null, null);
    }

    public static void open(ShardedCore plugin, Player player, FileConfiguration config, String title,
                            List<Entry> entries, int page, String kind, boolean limitedPage,
                            Consumer<Integer> openPage, Runnable extraClick, Consumer<Entry> select,
                            Runnable backClick, Runnable clearClick) {
        int rows = Math.max(3, Math.min(6, config.getInt("gui.rows", 6)));
        int[] area = GuiButtons.inner(rows);
        int per = area.length;
        int pages = Math.max(1, (entries.size() + per - 1) / per);
        int current = Math.max(0, Math.min(page, pages - 1));
        String guide = config.getString("gui.guide", kind == null || kind.isBlank() ? "Guide" : kind);
        String preview = config.getString("gui.preview-name", ColorUtil.strip(title));
        String resolved = Text.apply(config.getString("gui.title", preview),
                "guide", guide, "name", preview, "page", String.valueOf(current + 1));
        if (resolved == null || resolved.isBlank()) resolved = preview;
        Menus.Menu menu = plugin.menus().create(player, resolved, rows);
        int start = current * per;
        boolean bundles = config.getBoolean("gui.colored-bundles", true);
        Material fallback = Sounds.material(config.getString("gui.item-material", "BUNDLE"), Material.BUNDLE);
        for (int i = 0; i < per && start + i < entries.size(); i++) {
            Entry entry = entries.get(start + i);
            List<String> lore = lore(config, entry, kind);
            String prettyName = entry.title() == null ? "" : ColorUtil.strip(entry.title());
            String name = Text.apply(config.getString("gui.item-name", "%color%&l%name%"),
                    "color", entry.color(), "name", prettyName, "kind", kind, "display", entry.display());
            Material icon = bundles ? GuiButtons.bundleMaterial(entry.color()) : fallback;
            menu.set(area[i], Items.named(icon, name, lore), event -> {
                event.setCancelled(true);
                GuiButtons.play(player, "click");
                select.accept(entry);
            });
        }
        if (current > 0) {
            int slot = config.getInt("gui.previous.slot", GuiButtons.slot("previous", lastRow(rows) + 3));
            menu.set(slot, GuiButtons.previous(player, "page", String.valueOf(current)), event -> {
                event.setCancelled(true);
                GuiButtons.play(player, "click");
                openPage.accept(current - 1);
            });
        }
        if (current + 1 < pages) {
            int slot = config.getInt("gui.next.slot", GuiButtons.slot("next", lastRow(rows) + 5));
            menu.set(slot, GuiButtons.next(player, "page", String.valueOf(current + 2)), event -> {
                event.setCancelled(true);
                GuiButtons.play(player, "click");
                openPage.accept(current + 1);
            });
        }
        ConfigurationSection extra = config.getConfigurationSection("gui.extra");
        if (extra != null && extra.getBoolean("enabled", true) && extraClick != null && !limitedPage) {
            menu.set(extra.getInt("slot", lastRow(rows) + 4), Items.fromSection(extra, player), event -> {
                event.setCancelled(true);
                GuiButtons.play(player, "click");
                extraClick.run();
            });
        }
        if (limitedPage && backClick != null) {
            int slot = config.getInt("gui.limited-back.slot", config.getInt("gui.back.slot", 49));
            menu.set(slot, GuiButtons.back(player), event -> {
                event.setCancelled(true);
                GuiButtons.play(player, "click");
                backClick.run();
            });
        }
        if (clearClick != null) {
            ConfigurationSection clear = config.getConfigurationSection("gui.clear");
            if (clear != null && clear.getBoolean("enabled", true)) {
                menu.set(clear.getInt("slot", 4), Items.fromSection(clear, player), event -> {
                    event.setCancelled(true);
                    GuiButtons.play(player, "click");
                    clearClick.run();
                });
            }
        }
        ConfigurationSection close = config.getConfigurationSection("gui.close");
        if (close != null && close.getBoolean("enabled", false)) {
            menu.set(close.getInt("slot", GuiButtons.slot("close", lastRow(rows) + 4)),
                    Items.fromSection(close, player), event -> {
                        event.setCancelled(true);
                        player.closeInventory();
                    });
        } else if (!limitedPage && extraClick == null && (extra == null || !extra.getBoolean("enabled", true))) {
            int slot = config.getInt("gui.close.slot", GuiButtons.slot("close", lastRow(rows) + 4));
            if (config.getBoolean("gui.close.enabled", kind != null && kind.toLowerCase(Locale.ROOT).contains("colour"))) {
                menu.set(slot, GuiButtons.close(player), event -> {
                    event.setCancelled(true);
                    player.closeInventory();
                });
            }
        }
        GuiButtons.border(menu);
        plugin.menus().open(player, menu);
        GuiButtons.play(player, "open");
        if (config.isConfigurationSection("sounds.open")) {
            Sounds.play(player, config.getConfigurationSection("sounds.open"));
        }
    }

    public static List<String> lore(FileConfiguration config, Entry entry, String kind) {
        String owned = config.getString("gui.status-owned", "%color%Status: &#94FF00&nOwned");
        String locked = config.getString("gui.status-locked", "%color%Status: &#FF2727&nLocked");
        List<String> template = config.getStringList("gui.lore");
        if (template.isEmpty()) {
            template = List.of(
                    "&8Description",
                    "",
                    "%color%Information:",
                    "%color%| &fClick to equip",
                    "%color%| &fthis %kind%.",
                    "",
                    "%status%",
                    "",
                    "%click%"
            );
        }
        String click = config.getString("gui.click-footer",
                "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK To Equip");
        return Text.applyList(new ArrayList<>(template),
                "color", entry.color(),
                "name", entry.title(),
                "display", entry.display(),
                "kind", kind == null || kind.isBlank() ? "tag" : kind,
                "description", entry.description(),
                "status", Text.apply(entry.owned() ? owned : locked, "color", entry.color(), "name", entry.title()),
                "click", click);
    }

    public static String colorOf(String text, String fallback) {
        return ColorUtil.colorCode(ColorUtil.firstHex(text, fallback));
    }

    public static String pretty(String id) {
        return GuiButtons.pretty(id);
    }

    private static int lastRow(int rows) {
        return (Math.max(1, rows) - 1) * 9;
    }

    public record Entry(String id, String title, String display, String description, String color, boolean owned) {
    }
}
