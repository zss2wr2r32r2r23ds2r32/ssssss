package com.sharded.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.MusicInstrument;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** Plays goat horn / event sounds to players. */
public final class EventSounds {

    private EventSounds() {
    }

    public static MusicInstrument parseInstrument(String raw, MusicInstrument fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "PONDER", "PONDER_GOAT_HORN" -> MusicInstrument.PONDER_GOAT_HORN;
            case "SING", "SING_GOAT_HORN" -> MusicInstrument.SING_GOAT_HORN;
            case "SEEK", "SEEK_GOAT_HORN" -> MusicInstrument.SEEK_GOAT_HORN;
            case "FEEL", "FEEL_GOAT_HORN" -> MusicInstrument.FEEL_GOAT_HORN;
            case "ADMIRE", "ADMIRE_GOAT_HORN" -> MusicInstrument.ADMIRE_GOAT_HORN;
            case "CALL", "CALL_GOAT_HORN" -> MusicInstrument.CALL_GOAT_HORN;
            case "YEARN", "YEARN_GOAT_HORN" -> MusicInstrument.YEARN_GOAT_HORN;
            case "DREAM", "DREAM_GOAT_HORN" -> MusicInstrument.DREAM_GOAT_HORN;
            default -> fallback;
        };
    }

    public static void playInstrument(Player player, MusicInstrument instrument) {
        Location loc = player.getLocation();
        player.playSound(loc, instrument.getSound(), SoundCategory.RECORDS, 1f, 1f);
    }

    public static void playInstrumentToWorlds(MusicInstrument instrument, List<String> worlds) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (worlds == null || worlds.isEmpty()) {
                playInstrument(player, instrument);
                continue;
            }
            for (String world : worlds) {
                if (world.equalsIgnoreCase(player.getWorld().getName())) {
                    playInstrument(player, instrument);
                    break;
                }
            }
        }
    }
}
