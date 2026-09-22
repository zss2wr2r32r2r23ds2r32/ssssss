package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.koth.KothModule;
import com.sharded.core.modules.outpost.OutpostModule;
import java.util.Locale;
import org.bukkit.Location;

public final class EventPlaceholders {
   private EventPlaceholders() {
   }

   public static String apply(String input) {
      if (input == null || input.indexOf('%') < 0) {
         return input == null ? "" : input;
      }
      boolean outpostLine = contains(input, "%next_outpost%", "%outpost_player%", "%percentage%", "%outpost_");
      boolean kothLine = contains(input, "%next_koth%", "%koth_player%", "%koth_");
      if (outpostLine && !kothLine) {
         return applyOutpost(input);
      }
      return applyKoth(input);
   }

   public static String applyKoth(String input) {
      if (input == null || input.indexOf('%') < 0) {
         return input == null ? "" : input;
      }
      KothModule koth = module(KothModule.class);
      Location center = koth == null ? null : koth.regionCenter();
      String points = koth == null ? "0" : String.format(Locale.US, "%.0f", koth.leaderPoints());
      String player = koth == null ? "None" : koth.leaderName();
      return input.replace("%x%", coord(center, 0))
         .replace("%y%", coord(center, 1))
         .replace("%z%", coord(center, 2))
         .replace("%time%", kothTime())
         .replace("%next_koth%", kothNext())
         .replace("%points%", points)
         .replace("%koth_player%", player);
   }

   public static String applyOutpost(String input) {
      if (input == null || input.indexOf('%') < 0) {
         return input == null ? "" : input;
      }
      OutpostModule outpost = module(OutpostModule.class);
      Location center = outpost == null ? null : outpost.regionCenter();
      String percent = outpost == null ? "0" : String.format(Locale.US, "%.0f", outpost.capturePercent());
      String player = outpost == null ? "N/A" : outpost.capturerName();
      return input.replace("%x%", coord(center, 0))
         .replace("%y%", coord(center, 1))
         .replace("%z%", coord(center, 2))
         .replace("%time%", outpostTime())
         .replace("%next_outpost%", outpostNext())
         .replace("%points%", percent)
         .replace("%percentage%", percent)
         .replace("%outpost_player%", player);
   }

   public static String kothCoord(int axis) {
      KothModule koth = module(KothModule.class);
      return coord(koth == null ? null : koth.regionCenter(), axis);
   }

   public static String outpostCoord(int axis) {
      OutpostModule outpost = module(OutpostModule.class);
      return coord(outpost == null ? null : outpost.regionCenter(), axis);
   }

   public static String kothTime() {
      KothModule koth = module(KothModule.class);
      return TimeFormat.hms(koth == null ? 0L : koth.millisUntilStart());
   }

   public static String kothNext() {
      KothModule koth = module(KothModule.class);
      return TimeFormat.hms(koth == null ? 0L : koth.millisUntilNext());
   }

   public static String kothPoints() {
      KothModule koth = module(KothModule.class);
      return koth == null ? "0" : String.format(Locale.US, "%.0f", koth.leaderPoints());
   }

   public static String kothPlayer() {
      KothModule koth = module(KothModule.class);
      return koth == null ? "None" : koth.leaderName();
   }

   public static String outpostTime() {
      OutpostModule outpost = module(OutpostModule.class);
      return TimeFormat.hms(outpost == null ? 0L : outpost.displayTimeMs());
   }

   public static String outpostNext() {
      OutpostModule outpost = module(OutpostModule.class);
      return TimeFormat.hms(outpost == null ? 0L : outpost.millisUntilStart());
   }

   public static String outpostPoints() {
      OutpostModule outpost = module(OutpostModule.class);
      return outpost == null ? "0" : String.format(Locale.US, "%.0f", outpost.capturePercent());
   }

   public static String outpostPlayer() {
      OutpostModule outpost = module(OutpostModule.class);
      return outpost == null ? "N/A" : outpost.capturerName();
   }

   private static boolean contains(String input, String... tokens) {
      for (String token : tokens) {
         if (input.contains(token)) {
            return true;
         }
      }
      return false;
   }

   private static String coord(Location location, int axis) {
      if (location == null || location.getWorld() == null) {
         return "0";
      }
      int value = switch (axis) {
         case 0 -> location.getBlockX();
         case 1 -> location.getBlockY();
         default -> location.getBlockZ();
      };
      return String.valueOf(value);
   }

   private static <T extends Module> T module(Class<T> type) {
      ShardedCore plugin = ShardedCore.get();
      return plugin == null ? null : plugin.modules().get(type);
   }
}
