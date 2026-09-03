package com.shardedcore.eventcore.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.util.Map;

/** Config-driven titles and sounds, shared by every module that talks to players. */
public final class Feedback {

    private Feedback() {
    }

    /** Reads a {@code fade-in / stay / fade-out} block expressed in ticks. */
    public static Title.Times times(ConfigurationSection section, int fadeIn, int stay, int fadeOut) {
        int in = section == null ? fadeIn : section.getInt("fade-in", fadeIn);
        int hold = section == null ? stay : section.getInt("stay", stay);
        int out = section == null ? fadeOut : section.getInt("fade-out", fadeOut);
        return Title.Times.times(ticks(in), ticks(hold), ticks(out));
    }

    public static Duration ticks(int ticks) {
        return Duration.ofMillis(Math.max(0, ticks) * 50L);
    }

    /** Returns {@code null} when the section is absent or has {@code enabled: false}. */
    public static Sound sound(ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", true)) {
            return null;
        }
        String key = section.getString("key", "");
        if (key.isBlank()) {
            return null;
        }
        Key parsed = parseKey(key);
        if (parsed == null) {
            return null;
        }
        return Sound.sound(parsed, Sound.Source.MASTER,
                (float) section.getDouble("volume", 1.0D),
                (float) section.getDouble("pitch", 1.0D));
    }

    private static Key parseKey(String raw) {
        try {
            return Key.key(raw.toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static void play(Audience audience, Sound sound) {
        if (sound != null) {
            audience.playSound(sound);
        }
    }

    /**
     * Shows a title to every online player in one call. The server audience fans
     * out internally, which avoids allocating a new packet wrapper per player.
     */
    public static void broadcastTitle(String titleRaw, String subtitleRaw, Title.Times times,
                                      Map<String, String> placeholders) {
        Component titleComponent = titleRaw == null || titleRaw.isEmpty()
                ? Component.empty() : Text.parse(titleRaw, placeholders);
        Component subtitleComponent = subtitleRaw == null || subtitleRaw.isEmpty()
                ? Component.empty() : Text.parse(subtitleRaw, placeholders);
        Bukkit.getServer().showTitle(Title.title(titleComponent, subtitleComponent, times));
    }

    public static void clearTitles() {
        Bukkit.getServer().clearTitle();
    }
}
