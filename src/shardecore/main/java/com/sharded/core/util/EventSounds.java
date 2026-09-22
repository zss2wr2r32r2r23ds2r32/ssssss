package com.sharded.core.util;

import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.MusicInstrument;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

public final class EventSounds {
   private EventSounds() {
   }

   public static MusicInstrument parseInstrument(String raw, MusicInstrument fallback) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.trim().toUpperCase(Locale.ROOT);

         return switch (s) {
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
      } else {
         return fallback;
      }
   }

   public static void playInstrument(Player player, MusicInstrument instrument) {
      if (PlayerToggles.eventSounds(player)) {
         Location location = player.getLocation();
         player.playSound(location, instrument.getSound(), SoundCategory.RECORDS, 1.0F, 1.0F);
      }
   }

   public static void playInstrumentToWorlds(MusicInstrument instrument, List<String> worlds) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (worlds != null && !worlds.isEmpty()) {
            for (String s : worlds) {
               if (s.equalsIgnoreCase(player.getWorld().getName())) {
                  playInstrument(player, instrument);
                  break;
               }
            }
         } else {
            playInstrument(player, instrument);
         }
      }
   }
}
