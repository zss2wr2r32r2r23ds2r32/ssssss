package com.sharded.core.util;

import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks plugin-owned inventories so event handlers can resolve holders without calling
 * {@link Inventory#getHolder()}, which triggers expensive block-entity snapshots (e.g. shulker boxes).
 */
public final class TrackedInventories {

    private static final Map<Inventory, Object> HOLDERS = new WeakHashMap<>();

    private TrackedInventories() {
    }

    public static void track(Inventory inventory, Object holder) {
        if (inventory == null || holder == null) return;
        HOLDERS.put(inventory, holder);
    }

    public static Object lookup(Inventory inventory) {
        if (inventory == null) return null;
        return HOLDERS.get(inventory);
    }

    public static Object untrack(Inventory inventory) {
        if (inventory == null) return null;
        return HOLDERS.remove(inventory);
    }

    public static boolean isTracked(Inventory inventory) {
        return inventory != null && HOLDERS.containsKey(inventory);
    }

    @SuppressWarnings("unchecked")
    public static <T> T lookup(Inventory inventory, Class<T> type) {
        Object holder = lookup(inventory);
        if (holder == null || !type.isInstance(holder)) return null;
        return (T) holder;
    }

    @SuppressWarnings("unchecked")
    public static <T> T untrack(Inventory inventory, Class<T> type) {
        Object holder = untrack(inventory);
        if (holder == null || !type.isInstance(holder)) return null;
        return (T) holder;
    }
}
