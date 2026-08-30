package com.shardedcore.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class Sounds {

    private Sounds() {
    }

    public static void play(Player player, ConfigurationSection section) {
        if (player == null || section == null) return;
        if (section.isConfigurationSection("sound") && section.getConfigurationSection("sound") != null
                && section.getConfigurationSection("sound").contains("enabled")) {
            play(player, section.getConfigurationSection("sound"));
            return;
        }
        if (!section.getBoolean("enabled", true)) return;
        String name = section.getString("sound", "");
        if (name == null || name.isBlank()) return;
        float volume = (float) section.getDouble("volume", 1.0);
        float pitch = (float) section.getDouble("pitch", 1.0);
        play(player, name, volume, pitch);
    }

    public static void play(Player player, String name, float volume, float pitch) {
        if (player == null || name == null || name.isBlank()) return;
        String key = name.trim().toLowerCase().replace("minecraft:", "");
        player.playSound(player.getLocation(), key, volume, pitch);
    }

    public static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        Material matched = Material.matchMaterial(name);
        return matched == null ? fallback : matched;
    }
}
