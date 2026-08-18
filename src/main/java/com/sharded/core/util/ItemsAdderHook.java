package com.sharded.core.util;

import org.bukkit.inventory.ItemStack;

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

    /** Parses material strings: itemsadder-ns:id, minecraft:stone, or STONE. */
    public static ItemStack parseItem(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.contains(":") && !raw.toLowerCase(java.util.Locale.ROOT).startsWith("minecraft:")) {
            for (String id : candidateIds(raw)) {
                ItemStack ia = getItem(id);
                if (ia != null) return ia;
            }
        }
        String name = raw;
        if (name.contains(":")) name = name.substring(name.lastIndexOf(':') + 1);
        var material = org.bukkit.Material.matchMaterial(name.toUpperCase(java.util.Locale.ROOT));
        return material == null ? null : new ItemStack(material);
    }

    private static java.util.List<String> candidateIds(String raw) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        ids.add(raw);
        if (raw.startsWith("itemsadder-")) {
            ids.add(raw.substring("itemsadder-".length()));
        }
        if (raw.contains(":")) {
            String ns = raw.substring(0, raw.indexOf(':'));
            String item = raw.substring(raw.indexOf(':') + 1);
            if (ns.startsWith("itemsadder-")) {
                ids.add(ns.substring("itemsadder-".length()) + ":" + item);
            }
        }
        return ids;
    }
}
