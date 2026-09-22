package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.killstreaks.KillstreaksModule;
import com.sharded.core.modules.koth.KothModule;
import com.sharded.core.modules.outpost.OutpostModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

public final class PlaceholderUtil {
   private PlaceholderUtil() {
   }

   public static String apply(Player player, String input) {
      if (input != null && !input.isEmpty()) {
         String s = applyInternal(input);
         if (player != null) {
            s = applyPlayer(player, s);
         }
         if (player != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            s = PlaceholderAPI.setPlaceholders(player, s);
         }

         return s;
      } else {
         return "";
      }
   }

   public static List<String> applyList(Player player, List<String> lines) {
      List<String> list = new ArrayList<>(lines.size());

      for (String s : lines) {
         list.add(apply(player, s));
      }

      return list;
   }

   private static String applyPlayer(Player player, String input) {
      if (input.indexOf('%') < 0) {
         return input;
      }
      String out = input;
      ShardedCore plugin = ShardedCore.get();
      if (out.contains("%nextkoth%") || out.contains("%nextoutpost%")) {
         KothModule koth = plugin == null ? null : plugin.modules().get(KothModule.class);
         OutpostModule outpost = plugin == null ? null : plugin.modules().get(OutpostModule.class);
         out = out.replace("%nextkoth%", TimeFormat.hms(koth == null ? 0L : koth.millisUntilStart()))
            .replace("%nextoutpost%", TimeFormat.hms(outpost == null ? 0L : outpost.millisUntilStart()));
      }
      if (out.contains("%killstreak%")) {
         KillstreaksModule streaks = plugin == null ? null : plugin.modules().get(KillstreaksModule.class);
         int streak = streaks == null ? 0 : streaks.streak(player.getUniqueId());
         out = out.replace("%killstreak%", String.valueOf(streak));
      }
      if (out.contains("%kills%")) {
         out = out.replace("%kills%", String.valueOf(player.getStatistic(Statistic.PLAYER_KILLS)));
      }
      if (out.contains("%playtime%")) {
         long minutes = Math.max(0L, player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200L);
         out = out.replace("%playtime%", Text.formatPlaytime(minutes));
      }
      return out;
   }

   private static String applyInternal(String input) {
      ShardedCore shardedcore = ShardedCore.get();
      if (shardedcore == null) {
         return input;
      } else {
         OutpostModule outpostmodule = shardedcore.modules().get(OutpostModule.class);
         long i = outpostmodule == null ? 0L : outpostmodule.millisUntilStart();
         long outpostLeft = outpostmodule == null ? 0L : outpostmodule.displayTimeMs();
         String s = TimeFormat.hms(outpostLeft);
         String nextOutpost = TimeFormat.hms(i);
         KothModule kothmodule = shardedcore.modules().get(KothModule.class);
         long j = kothmodule == null ? 0L : kothmodule.millisUntilStart();
         long nextKoth = kothmodule == null ? 0L : kothmodule.millisUntilNext();
         String s1 = TimeFormat.hms(j);
         String nextKothText = TimeFormat.hms(nextKoth);
         String s2 = input.replace("%shardedcore_outpost_time%", s)
            .replace("%shardedcore_outpost_countdown%", nextOutpost)
            .replace("%outpost_time%", s)
            .replace("%next_outpost%", nextOutpost)
            .replace("%shardedcore_koth_time%", s1)
            .replace("%shardedcore_koth_countdown%", s1)
            .replace("%koth_time%", s1)
            .replace("%next_koth%", nextKothText);
         if (outpostmodule != null) {
            String percent = String.format(Locale.US, "%.0f", outpostmodule.capturePercent());
            Location center = outpostmodule.regionCenter();
            s2 = s2.replace("%shardedcore_outpost_active%", outpostmodule.isActive() ? "true" : "false")
               .replace("%shardedcore_outpost_capturer%", outpostmodule.contestingName())
               .replace("%outpost_capturer%", outpostmodule.contestingName())
               .replace("%outpost_contesting%", outpostmodule.contestingName())
               .replace("%outpost_player%", outpostmodule.capturerName())
               .replace("%shardedcore_outpost_percent%", percent)
               .replace("%outpost_percent%", percent)
               .replace("%outpost_percentage%", percent)
               .replace("%outpost_points%", percent)
               .replace("%outpost_x%", center == null ? "0" : String.valueOf(center.getBlockX()))
               .replace("%outpost_y%", center == null ? "0" : String.valueOf(center.getBlockY()))
               .replace("%outpost_z%", center == null ? "0" : String.valueOf(center.getBlockZ()))
               .replace("%shardedcore_outpost_bar%", outpostmodule.progressBar())
               .replace("%outpost_bar%", outpostmodule.progressBar())
               .replace("%outpost_contested%", outpostmodule.isContested() ? "Contested" : "Uncontested")
               .replace("%shardedcore_outpost_empty_time%", TimeFormat.hms(outpostmodule.emptyTimeRemainingMs()));
         }

         if (kothmodule != null) {
            Location center = kothmodule.regionCenter();
            String points = String.format(Locale.US, "%.0f", kothmodule.leaderPoints());
            s2 = s2.replace("%shardedcore_koth_active%", kothmodule.isActive() ? "true" : "false")
               .replace("%shardedcore_koth_leader%", kothmodule.leaderName())
               .replace("%koth_player%", kothmodule.leaderName())
               .replace("%shardedcore_koth_leader_points%", points)
               .replace("%koth_points%", points)
               .replace("%koth_x%", center == null ? "0" : String.valueOf(center.getBlockX()))
               .replace("%koth_y%", center == null ? "0" : String.valueOf(center.getBlockY()))
               .replace("%koth_z%", center == null ? "0" : String.valueOf(center.getBlockZ()))
               .replace("%koth_percent%", String.format(Locale.US, "%.0f", kothmodule.eventPercent()))
               .replace("%shardedcore_koth_percent%", String.format(Locale.US, "%.0f", kothmodule.eventPercent()))
               .replace("%koth_bar%", kothmodule.progressBar())
               .replace("%shardedcore_koth_bar%", kothmodule.progressBar());
         }

         return s2;
      }
   }
}
