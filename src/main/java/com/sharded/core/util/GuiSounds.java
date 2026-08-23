package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Plays configured GUI sounds from gui-navigation.yml. */
public final class GuiSounds {

    private final ShardedCore plugin;
    private YamlConfiguration config;

    public GuiSounds(ShardedCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(
                new java.io.File(plugin.getDataFolder(), "gui-navigation.yml"));
    }

    public void play(Player player, String key) {
        if (player == null || config == null) return;
        String soundName = config.getString("sounds." + key);
        if (soundName == null || soundName.isBlank()) return;
        try {
            player.playSound(player.getLocation(), Sound.valueOf(soundName.toUpperCase()), 1f, 1f);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
