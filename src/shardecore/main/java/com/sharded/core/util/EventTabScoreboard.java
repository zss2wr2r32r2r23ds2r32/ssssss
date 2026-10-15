package com.sharded.core.util;

import org.bukkit.Bukkit;

public final class EventTabScoreboard {
   private EventTabScoreboard() {
   }

   public static void apply(boolean enabled, String command) {
      if (!enabled || command == null || command.isBlank()) {
         return;
      }
      String cmd = command.startsWith("/") ? command.substring(1) : command;
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
   }

   public static String bar(double percent) {
      double clamped = Math.max(0.0, Math.min(100.0, percent));
      int filled = (int) Math.round(clamped / 10.0);
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < 10; i++) {
         builder.append(i < filled ? "█" : "░");
      }
      return builder.toString();
   }
}
