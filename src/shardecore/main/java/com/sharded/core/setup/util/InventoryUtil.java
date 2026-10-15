package com.sharded.core.setup.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class InventoryUtil {

    private InventoryUtil() {
    }

    public static int availableSpace(Player player, ItemStack prototype) {
        if (prototype == null || prototype.getType().isAir()) {
            return 0;
        }
        int maxStack = prototype.getMaxStackSize();
        int space = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                space += maxStack;
            } else if (stack.isSimilar(prototype) && stack.getAmount() < maxStack) {
                space += maxStack - stack.getAmount();
            }
        }
        return space;
    }

    public static int giveItems(Player player, ItemStack prototype, int amount) {
        if (amount <= 0 || prototype == null || prototype.getType().isAir()) {
            return 0;
        }
        int maxStack = prototype.getMaxStackSize();
        int toGive = Math.min(amount, availableSpace(player, prototype));
        int given = 0;
        while (given < toGive) {
            ItemStack batch = prototype.clone();
            batch.setAmount(Math.min(maxStack, toGive - given));
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(batch);
            if (!leftover.isEmpty()) {
                break;
            }
            given += batch.getAmount();
        }
        return given;
    }
}
