package com.sharded.core.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** Player heads from {@code PLAYER_HEAD}, {@code head:Name}, or base64 texture strings. */
public final class HeadUtil {

    private HeadUtil() {
    }

    public static ItemStack parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.equalsIgnoreCase("PLAYER_HEAD") || value.equalsIgnoreCase("%player_head%")) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
        if (value.regionMatches(true, 0, "basehead-", 0, 9)) {
            return textureHead(value.substring(9).trim());
        }
        if (value.regionMatches(true, 0, "head:", 0, 5)) {
            return namedHead(value.substring(5).trim());
        }
        if (value.regionMatches(true, 0, "texture:", 0, 8)) {
            return textureHead(value.substring(8).trim());
        }
        if (value.startsWith("eyJ")) {
            return textureHead(value);
        }
        return null;
    }

    public static ItemStack applyViewer(ItemStack stack, Player viewer) {
        if (stack == null || viewer == null || stack.getType() != Material.PLAYER_HEAD) return stack;
        ItemStack copy = stack.clone();
        SkullMeta meta = (SkullMeta) copy.getItemMeta();
        if (meta == null) return copy;
        meta.setOwningPlayer(viewer);
        copy.setItemMeta(meta);
        return copy;
    }

    public static ItemStack namedHead(String playerName) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack textureHead(String base64) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta == null) return stack;
        UUID id = UUID.nameUUIDFromBytes(("sharded-head:" + base64).getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createProfile(id, "head");
        profile.setProperty(new ProfileProperty("textures", base64));
        meta.setPlayerProfile(profile);
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isViewerHeadMaterial(String raw) {
        if (raw == null) return false;
        String lower = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.equals("player_head") || lower.equals("%player_head%");
    }

    public static boolean isHeadMaterial(String raw) {
        if (raw == null) return false;
        String lower = raw.toLowerCase(Locale.ROOT);
        return lower.equals("player_head")
                || lower.equals("%player_head%")
                || lower.startsWith("basehead-")
                || lower.startsWith("head:")
                || lower.startsWith("texture:")
                || raw.startsWith("eyJ");
    }
}
