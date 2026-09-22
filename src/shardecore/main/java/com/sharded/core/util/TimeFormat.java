package com.sharded.core.util;

public final class TimeFormat {
   private TimeFormat() {
   }

   public static String hms(long millis) {
      long i = Math.max(0L, millis / 1000L);
      long j = i / 3600L;
      long k = i % 3600L / 60L;
      long l = i % 60L;
      return j + "h " + k + "m " + l + "s";
   }

   public static String replacePlaceholders(String input, long millis) {
      if (input == null) {
         return "";
      } else {
         long i = Math.max(0L, millis / 1000L);
         long j = i / 3600L;
         long k = i % 3600L / 60L;
         long l = i % 60L;
         return input.replace("%hours%", String.valueOf(j))
            .replace("%minutes%", String.valueOf(k))
            .replace("%seconds%", String.valueOf(l))
            .replace("%time%", hms(millis));
      }
   }
}
