package dev.sharded.core.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;

public final class SoundUtil {
    private SoundUtil() {
    }

    public static void play(Player player, String key, float volume, float pitch) {
        if (player == null || key == null || key.isBlank()) {
            return;
        }
        try {
            player.playSound(Sound.sound(Key.key(key.contains(":") ? key : "minecraft:" + key), Sound.Source.MASTER, volume, pitch));
        } catch (RuntimeException ignored) {
        }
    }
}
