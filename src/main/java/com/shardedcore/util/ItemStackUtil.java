package com.shardedcore.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Locale;

public final class ItemStackUtil {

    private ItemStackUtil() {
    }

    public static byte[] serialize(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return new byte[0];
        return stack.serializeAsBytes();
    }

    public static ItemStack deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    public static String displayName(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return "Unknown";
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName());
        }
        return formatMaterial(stack.getType());
    }

    public static String formatMaterial(Material material) {
        if (material == null) return "Unknown";
        String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (name.isEmpty()) return "Unknown";
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public static boolean similar(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        if (a.getAmount() <= 0 || b.getAmount() <= 0) return false;
        ItemMeta metaA = a.getItemMeta();
        ItemMeta metaB = b.getItemMeta();
        if (metaA instanceof PotionMeta pa && metaB instanceof PotionMeta pb) {
            return pa.getBasePotionType() == pb.getBasePotionType();
        }
        return a.isSimilar(b) || a.getType() == b.getType();
    }
}
