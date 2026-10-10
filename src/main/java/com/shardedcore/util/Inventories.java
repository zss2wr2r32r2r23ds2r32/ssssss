package com.shardedcore.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Collection;
import java.util.Map;

public final class Inventories {

    private Inventories() {
    }

    public static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    public static boolean hasSpace(Player player, Collection<ItemStack> items) {
        if (player == null || items == null || items.isEmpty()) return true;
        ItemStack[] contents = cloneStorage(player.getInventory());
        for (ItemStack item : items) {
            if (isAir(item)) continue;
            if (!add(contents, item.clone())) return false;
        }
        return true;
    }

    public static boolean hasSpace(Player player, Map<Integer, ItemStack> items) {
        return items == null || hasSpace(player, items.values());
    }

    public static boolean hasSpace(Player player, ItemStack item) {
        return isAir(item) || hasSpace(player, java.util.List.of(item));
    }

    public static int emptyStorage(Player player) {
        int empty = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        if (contents == null) return 0;
        for (ItemStack item : contents) {
            if (isAir(item)) empty++;
        }
        return empty;
    }

    private static ItemStack[] cloneStorage(PlayerInventory inventory) {
        ItemStack[] contents = inventory.getStorageContents();
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            clone[i] = contents[i] == null ? null : contents[i].clone();
        }
        return clone;
    }

    private static boolean add(ItemStack[] contents, ItemStack item) {
        int left = item.getAmount();
        int max = item.getMaxStackSize();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack slot = contents[i];
            if (isAir(slot) || !slot.isSimilar(item)) continue;
            int space = max - slot.getAmount();
            if (space <= 0) continue;
            int take = Math.min(space, left);
            slot.setAmount(slot.getAmount() + take);
            left -= take;
        }
        for (int i = 0; i < contents.length && left > 0; i++) {
            if (!isAir(contents[i])) continue;
            ItemStack placed = item.clone();
            placed.setAmount(Math.min(max, left));
            contents[i] = placed;
            left -= placed.getAmount();
        }
        return left <= 0;
    }
}
