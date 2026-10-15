package com.sharded.core.setup.util;

import org.bukkit.Material;

import java.util.Locale;

public final class MaterialUtil {

    private MaterialUtil() {
    }

    public static Material resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.contains(",") || trimmed.contains("|")) {
            for (String part : trimmed.split("[,|]")) {
                Material material = resolveSingle(part.trim());
                if (material != null) {
                    return material;
                }
            }
            return null;
        }
        return resolveSingle(trimmed);
    }

    private static Material resolveSingle(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(raw.trim());
        if (material == null) {
            material = Material.getMaterial(raw.trim().toUpperCase(Locale.ROOT));
        }
        if (material == null || !material.isItem()) {
            return null;
        }
        return material;
    }

    public static Material resolve(String raw, Material fallback) {
        Material material = resolve(raw);
        return material != null ? material : fallback;
    }

    public static Material resolve(String raw, String fallback) {
        Material material = resolve(raw);
        if (material != null) {
            return material;
        }
        return resolve(fallback);
    }

    public static Material resolve(String raw, String... fallbacks) {
        Material material = resolve(raw);
        if (material != null) {
            return material;
        }
        if (fallbacks != null) {
            for (String fallback : fallbacks) {
                material = resolve(fallback);
                if (material != null) {
                    return material;
                }
            }
        }
        return null;
    }

    
    public static Material resolveWithFallbacks(String... names) {
        if (names == null) {
            return null;
        }
        for (String name : names) {
            Material material = resolve(name);
            if (material != null) {
                return material;
            }
        }
        return null;
    }
}
