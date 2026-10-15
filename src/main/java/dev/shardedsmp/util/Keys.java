package dev.shardedsmp.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class Keys {
    public static NamespacedKey specialObsidian;
    public static NamespacedKey pieceId;
    public static NamespacedKey counted;
    public static NamespacedKey eventWither;
    public static NamespacedKey eggHearts;
    public static NamespacedKey placedDiamondOre;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        specialObsidian = new NamespacedKey(plugin, "special_obsidian");
        pieceId = new NamespacedKey(plugin, "piece_id");
        counted = new NamespacedKey(plugin, "counted");
        eventWither = new NamespacedKey(plugin, "event_wither");
        eggHearts = new NamespacedKey(plugin, "dragon_egg_hearts");
        placedDiamondOre = new NamespacedKey(plugin, "placed_diamond_ore");
    }
}
