package com.shardedcore.util;

import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.WeakHashMap;

public final class TrackedInventories {

    private static final Map<Inventory, Object> HOLDERS = new WeakHashMap<>();

    private TrackedInventories() {
    }

    public static void track(Inventory inventory, Object holder) {
        if (inventory != null && holder != null) HOLDERS.put(inventory, holder);
    }

    @SuppressWarnings("unchecked")
    public static <T> T lookup(Inventory inventory, Class<T> type) {
        Object holder = inventory == null ? null : HOLDERS.get(inventory);
        if (holder == null || !type.isInstance(holder)) return null;
        return (T) holder;
    }

    @SuppressWarnings("unchecked")
    public static <T> T untrack(Inventory inventory, Class<T> type) {
        Object holder = inventory == null ? null : HOLDERS.remove(inventory);
        if (holder == null || !type.isInstance(holder)) return null;
        return (T) holder;
    }
}
