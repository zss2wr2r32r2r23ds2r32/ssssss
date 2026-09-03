package com.shardedcore.eventcore.util;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Item serialisation and armour-slot detection helpers. */
public final class Items {

    private static final String EMPTY = "";

    private Items() {
    }

    /**
     * Encodes a stack to Base64 using Paper's byte format, which runs the item
     * through the vanilla data fixers on read. That means kits created on 1.21.11
     * keep working after a future server upgrade.
     */
    public static String encode(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return EMPTY;
        }
        return Base64.getEncoder().encodeToString(stack.serializeAsBytes());
    }

    public static ItemStack decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static List<String> encodeAll(ItemStack[] stacks) {
        List<String> out = new ArrayList<>(stacks.length);
        for (ItemStack stack : stacks) {
            out.add(encode(stack));
        }
        return out;
    }

    public static ItemStack[] decodeAll(List<String> encoded, int size) {
        ItemStack[] out = new ItemStack[size];
        int limit = Math.min(size, encoded.size());
        for (int index = 0; index < limit; index++) {
            out[index] = decode(encoded.get(index));
        }
        return out;
    }

    /**
     * Which armour slot a material belongs in, or {@code null} if it is not armour.
     *
     * <p>Deliberately name based rather than using {@code Material#getEquipmentSlot},
     * so new armour materials added by future Minecraft versions are handled
     * without a plugin update.</p>
     */
    public static EquipmentSlot armorSlot(Material material) {
        if (material == null || material.isAir()) {
            return null;
        }
        String name = material.name();
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET") || name.equals("CARVED_PUMPKIN")) {
            return EquipmentSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) {
            return EquipmentSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return EquipmentSlot.FEET;
        }
        return null;
    }

    public static int armorIndex(EquipmentSlot slot) {
        // PlayerInventory#getArmorContents is ordered boots, leggings, chestplate, helmet.
        return switch (slot) {
            case FEET -> 0;
            case LEGS -> 1;
            case CHEST -> 2;
            case HEAD -> 3;
            default -> -1;
        };
    }

    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }
}
