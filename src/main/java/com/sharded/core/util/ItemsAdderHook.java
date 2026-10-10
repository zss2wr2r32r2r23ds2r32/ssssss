package com.sharded.core.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Soft hook for ItemsAdder custom items (itemsadder-namespace:id). */
public final class ItemsAdderHook {

    private static Boolean available;

    private ItemsAdderHook() {
    }

    public static boolean isAvailable() {
        if (available != null) return available;
        try {
            Class.forName("dev.lone.itemsadder.api.CustomStack");
            available = org.bukkit.Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
        } catch (Throwable t) {
            available = false;
        }
        return available;
    }

    public static ItemStack getItem(String id) {
        if (id == null || id.isBlank()) return null;
        if (!isAvailable()) return null;
        try {
            Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object stack = customStack.getMethod("getInstance", String.class).invoke(null, id);
            if (stack == null) return null;
            return (ItemStack) customStack.getMethod("getItemStack").invoke(stack);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Resolves an ItemsAdder id or material string, trying common namespace/case variants. */
    public static ItemStack resolve(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.contains(":") && !raw.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            for (String id : candidateIds(raw)) {
                ItemStack ia = getItem(id);
                if (ia != null) return ia;
            }
        }
        return parseVanillaMaterial(raw);
    }

    /** Parses material strings: itemsadder-ns:id, HATS:CROWN, minecraft:stone, or STONE. */
    public static ItemStack parseItem(String raw) {
        ItemStack resolved = resolve(raw);
        if (resolved != null) return resolved;
        return parseVanillaMaterial(raw);
    }

    private static ItemStack parseVanillaMaterial(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String name = raw;
        if (name.contains(":")) name = name.substring(name.lastIndexOf(':') + 1);
        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return material == null ? null : new ItemStack(material);
    }

    static List<String> candidateIds(String raw) {
        Set<String> ids = new LinkedHashSet<>();
        addVariants(ids, raw);
        if (raw.startsWith("itemsadder-")) {
            addVariants(ids, raw.substring("itemsadder-".length()));
        }
        if (raw.contains(":")) {
            String ns = raw.substring(0, raw.indexOf(':'));
            String item = raw.substring(raw.indexOf(':') + 1);
            if (ns.startsWith("itemsadder-")) {
                addVariants(ids, ns.substring("itemsadder-".length()) + ":" + item);
            }
            addVariants(ids, ns + ":" + item);
        }
        return new ArrayList<>(ids);
    }

    private static void addVariants(Set<String> ids, String id) {
        if (id == null || id.isBlank()) return;
        ids.add(id);
        ids.add(id.toLowerCase(Locale.ROOT));
        ids.add(id.toUpperCase(Locale.ROOT));
        if (!id.contains(":")) return;

        String ns = id.substring(0, id.indexOf(':'));
        String item = id.substring(id.indexOf(':') + 1);
        ids.add(ns.toLowerCase(Locale.ROOT) + ":" + item.toLowerCase(Locale.ROOT));
        ids.add(ns.toUpperCase(Locale.ROOT) + ":" + item.toUpperCase(Locale.ROOT));
        ids.add(ns.toUpperCase(Locale.ROOT) + ":" + item.toLowerCase(Locale.ROOT));

        String altItem = item.contains("_") ? item.replace("_", "") : item;
        if (!altItem.equals(item)) {
            ids.add(ns.toLowerCase(Locale.ROOT) + ":" + altItem.toLowerCase(Locale.ROOT));
            ids.add(ns.toUpperCase(Locale.ROOT) + ":" + altItem.toUpperCase(Locale.ROOT));
        }
    }
}
