package com.sharded.core.setup.gui;

import com.sharded.core.ShardedCore;
import com.sharded.core.setup.util.ItemBuilder;
import com.sharded.core.setup.util.MaterialUtil;
import com.sharded.core.setup.util.SoundUtil;
import com.sharded.core.setup.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GuiHelper {

    public static final int QTY_ITEM_SLOT = 22;
    public static final int QTY_BACK_SLOT = 39;
    public static final int QTY_CONFIRM_SLOT = 41;
    public static final int CATEGORY_BACK_SLOT = 40;

    private GuiHelper() {
    }

    public static Inventory create(String id, String title, int rows) {
        return Bukkit.createInventory(new GuiHolder(id), rows * 9, TextUtil.component(title));
    }

    public static void fillBorder(Inventory inventory) {
        ItemStack pane = ItemBuilder.glassPane();
        int size = inventory.getSize();
        int width = 9;
        int rows = size / width;
        for (int i = 0; i < size; i++) {
            int row = i / width;
            int col = i % width;
            if (row == 0 || row == rows - 1 || col == 0 || col == width - 1) {
                inventory.setItem(i, pane);
            }
        }
    }

    public static void fillDarkBorder(Inventory inventory) {
        ItemStack pane = ItemBuilder.darkGlassPane();
        int size = inventory.getSize();
        int width = 9;
        int rows = size / width;
        for (int i = 0; i < size; i++) {
            int row = i / width;
            int col = i % width;
            if (row == 0 || row == rows - 1 || col == 0 || col == width - 1) {
                inventory.setItem(i, pane);
            }
        }
    }

    /** Dark border + light (gray) fill for inner slots — crates-style preview GUIs. */
    public static void fillBorderedInner(Inventory inventory) {
        ItemStack dark = ItemBuilder.darkGlassPane();
        ItemStack light = ItemBuilder.glassPane();
        int size = inventory.getSize();
        int width = 9;
        int rows = size / width;
        for (int i = 0; i < size; i++) {
            int row = i / width;
            int col = i % width;
            boolean border = row == 0 || row == rows - 1 || col == 0 || col == width - 1;
            inventory.setItem(i, border ? dark : light);
        }
    }

    public static List<Integer> innerContentSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col < 8; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    public static void fillGlass(Inventory inventory) {
        ItemStack pane = ItemBuilder.glassPane();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane);
        }
    }

    public static void fillDarkGlass(Inventory inventory) {
        ItemStack pane = ItemBuilder.darkGlassPane();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane);
        }
    }

    public static void fillAllExcept(Inventory inventory, Integer... openSlots) {
        Set<Integer> allowed = new HashSet<>(Arrays.asList(openSlots));
        ItemStack pane = ItemBuilder.glassPane();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!allowed.contains(i)) {
                inventory.setItem(i, pane);
            }
        }
    }

    public static ItemStack backButton() {
        return backButton("Shop");
    }

    public static ItemStack backButton(String destination) {
        String label = destination == null || destination.isBlank() ? "Shop" : destination;
        return buttonFromConfig("back", Material.FLOWER_BANNER_PATTERN, "&#FF0000&lBACK", List.of(
                "&8Description",
                "",
                "&#FF0000Information:",
                "&#FF0000| &fClick to Navigate",
                "&#FF0000| &fBack to the Main GUI",
                "",
                "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Return"
        ), "%destination%", label);
    }

    public static ItemStack confirmButton(String priceLine) {
        String line = priceLine == null ? "Confirm" : priceLine;
        return buttonFromConfig("confirm", Material.LIME_DYE, "&#9FFF00&lCONFIRM", List.of(
                "&8Description",
                "",
                "&#9FFF00Information:",
                "&#9FFF00| &fClick to Confirm",
                "&#9FFF00| &fAnd Continue",
                "",
                "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Confirm"
        ), "%price%", line);
    }

    public static ItemStack cancelButton() {
        return buttonFromConfig("cancel", Material.RED_STAINED_GLASS_PANE, "&#FF2121&lCANCEL", List.of(
                "&8Description",
                "",
                "&#FF2121Information:",
                "&#FF2121| &fClick to Cancel",
                "&#FF2121| &fand Return to the main GUI",
                "",
                "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Cancel"
        ));
    }

    public static ItemStack closeButton() {
        return buttonFromConfig("close", Material.RED_DYE, "&#FF0000&lCLOSE", List.of(
                "&8Description",
                "",
                "&#FF0000Information:",
                "&#FF0000| &fClick to",
                "&#FF0000| &fClose this GUI",
                "",
                "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Close"
        ));
    }

    public static ItemStack nextPageButton() {
        return buttonFromConfig("next-page", Material.LIME_STAINED_GLASS_PANE, "&#9FFF00&lNEXT PAGE", List.of(
                "&8Description",
                "",
                "&#9FFF00Information:",
                "&#9FFF00| &fClick to Navigate",
                "&#9FFF00| &fTo The Next Page",
                "",
                "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Navigate"
        ));
    }

    public static ItemStack previousPageButton() {
        return buttonFromConfig("previous-page", Material.RED_STAINED_GLASS_PANE, "&#FF2121&lPREVIOUS PAGE", List.of(
                "&8Description",
                "",
                "&#FF2121Information:",
                "&#FF2121| &fClick to Return",
                "&#FF2121| &fto the Previous Page",
                "",
                "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Navigate"
        ));
    }

    public static ItemStack shardConfirmButton(String priceLine) {
        return confirmButton(priceLine);
    }

    private static ItemStack buttonFromConfig(String key, Material fallbackMat, String fallbackName,
                                              List<String> fallbackLore) {
        return buttonFromConfig(key, fallbackMat, fallbackName, fallbackLore, null, null);
    }

    private static ItemStack buttonFromConfig(String key, Material fallbackMat, String fallbackName,
                                              List<String> fallbackLore, String replaceKey, String replaceValue) {
        ShardedCore plugin = ShardedCore.get();
        if (plugin != null && plugin.guiNavigation() != null && plugin.guiNavigation().section(key) != null) {
            ItemStack fromNav = plugin.guiNavigation().build(key);
            if (replaceKey != null && replaceValue != null && fromNav != null) {
                return ItemBuilder.of(fromNav)
                        .name(plugin.guiNavigation().displayName(key, null).replace(replaceKey, replaceValue))
                        .lore(replaceLore(plugin.guiNavigation().lore(key, null), replaceKey, replaceValue))
                        .hideExtras()
                        .build();
            }
            return fromNav;
        }
        Material material = fallbackMat;
        String name = fallbackName;
        List<String> lore = new ArrayList<>(fallbackLore);
        if (plugin != null) {
            FileConfiguration config = plugin.getConfig();
            ConfigurationSection section = config.getConfigurationSection("gui-buttons." + key);
            if (section != null) {
                Material resolved = MaterialUtil.resolve(section.getString("material"), fallbackMat);
                if (resolved != null) {
                    material = resolved;
                }
                if (section.contains("name")) {
                    name = section.getString("name", fallbackName);
                }
                List<String> configured = section.getStringList("lore");
                if (configured != null && !configured.isEmpty()) {
                    lore = new ArrayList<>(configured);
                }
            }
        }
        if (replaceKey != null && replaceValue != null) {
            name = name.replace(replaceKey, replaceValue);
            lore = replaceLore(lore, replaceKey, replaceValue);
        }
        return ItemBuilder.of(material).name(name).lore(lore).hideExtras().build();
    }

    private static List<String> replaceLore(List<String> lore, String replaceKey, String replaceValue) {
        List<String> replaced = new ArrayList<>(lore.size());
        for (String line : lore) {
            replaced.add(line.replace(replaceKey, replaceValue));
        }
        return replaced;
    }

    /** Play page-turn sound; empty sound key = skip. Default: item.book.page_turn */
    public static void playPageTurn(Player player, FileConfiguration moduleConfig) {
        if (player == null) {
            return;
        }
        String sound = moduleConfig != null
                ? moduleConfig.getString("sound-page-turn", "item.book.page_turn")
                : "item.book.page_turn";
        SoundUtil.play(player, sound);
    }

    public static void playPageTurn(Player player, String sound) {
        if (player == null) {
            return;
        }
        SoundUtil.play(player, sound == null || sound.isBlank() ? "item.book.page_turn" : sound);
    }
}
