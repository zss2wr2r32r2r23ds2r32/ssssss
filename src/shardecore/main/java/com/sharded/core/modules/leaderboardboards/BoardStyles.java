package com.sharded.core.modules.leaderboardboards;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class BoardStyles {
   static final int DEFAULT_ENTRIES = 10;
   static final int CONFIG_VERSION = 3;
   private static final Set<String> BUILTIN = Set.of("kills", "tokens", "token", "deaths", "killstreaks", "killstreak", "elo", "duels", "playtime");

   private BoardStyles() {
   }

   static String statistic(String raw) {
      String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT);

      return switch (s) {
         case "duels", "wins", "duelwins" -> "elo";
         case "token" -> "tokens";
         case "killstreak", "streaks", "streak" -> "killstreaks";
         default -> s;
      };
   }

   static boolean builtin(String idOrStat) {
      return idOrStat != null && BUILTIN.contains(idOrStat.toLowerCase(Locale.ROOT));
   }

   static String color(String stat) {
      String s = statistic(stat);

      return switch (s) {
         case "tokens" -> "&#5C94FC";
         case "deaths" -> "&#FF5300";
         case "killstreaks" -> "&#FF0000";
         case "playtime" -> "&#5C94FC";
         default -> "&#FF007B";
      };
   }

   static String icon(String stat) {
      String s = statistic(stat);

      return switch (s) {
         case "tokens" -> "⛃";
         case "deaths" -> "☠";
         case "killstreaks" -> "\ud83e\ude93";
         case "elo" -> "\ud83c\udff9";
         case "playtime" -> "⌚";
         default -> "\ud83d\udde1";
      };
   }

   static String displayName(String stat) {
      String s = statistic(stat);

      return switch (s) {
         case "tokens" -> "MOST TOKENS";
         case "deaths" -> "MOST DEATHS";
         case "killstreaks" -> "HIGHEST KILLSTREAK";
         case "elo" -> "HIGHEST ELO";
         case "playtime" -> "MOST PLAYTIME";
         default -> "MOST KILLS";
      };
   }

   static String title(String stat) {
      String s = statistic(stat);
      String s1 = color(s);
      String s2 = icon(s);
      String s3 = displayName(s);
      return "tokens".equals(s) ? s1 + "&l" + s2 + " " + s3 + " &l" + s2 : s1 + s2 + " &l" + s3 + "&r" + s1 + " " + s2;
   }

   static List<String> lines(String stat, int entries) {
      String s = statistic(stat);
      String s1 = color(s);
      String s2 = icon(s);
      int i = Math.max(1, Math.min(20, entries));
      List<String> list = new ArrayList<>();
      list.add("&7ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅs");
      list.add(s1 + "Refreshes in &f%leaderboard_refresh%");
      list.add("&r");

      for (int j = 1; j <= i; j++) {
         list.add(s1 + "#" + j + " &f%leaderboard_" + s + "_name_" + j + "% &7| " + s1 + s2 + " " + s1 + "%leaderboard_" + s + "_value_" + j + "%");
      }

      list.add("&r");
      list.add(s1 + "#%leaderboard_" + s + "_rank% &7| &f%player_name% &7| " + s1 + s2 + s1 + "%leaderboard_" + s + "_self%");
      return list;
   }

   static void apply(BoardDefinition board) {
      if (board != null) {
         if (!"duels".equalsIgnoreCase(board.id) && !"duels".equalsIgnoreCase(board.statistic)) {
            board.statistic = statistic(board.statisticKey());
         } else {
            board.statistic = "elo";
         }

         board.entries = 10;
         board.title = title(board.statisticKey());
         board.lines = lines(board.statisticKey(), board.entries);
      }
   }
}
