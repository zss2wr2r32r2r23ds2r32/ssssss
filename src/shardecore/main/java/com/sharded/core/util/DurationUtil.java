package com.sharded.core.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationUtil {
   private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])", 2);
   private static final DateTimeFormatter EXPIRES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

   private DurationUtil() {
   }

   public static Long parseToMillis(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.trim();
         if (isPermanent(s)) {
            return null;
         } else {
            Matcher matcher = PART.matcher(s.toLowerCase(Locale.ROOT));
            long i = 0L;
            boolean flag = false;

            while (matcher.find()) {
               flag = true;
               long j = Long.parseLong(matcher.group(1));
               i += j * unitMillis(matcher.group(2).charAt(0));
            }

            if (!flag) {
               try {
                  long k = Long.parseLong(s);
                  return k <= 0L ? null : k * 1000L;
               } catch (NumberFormatException numberformatexception) {
                  return -1L;
               }
            } else {
               return i <= 0L ? -1L : i;
            }
         }
      } else {
         return null;
      }
   }

   public static Long expiresAt(String raw) {
      Long olong = parseToMillis(raw);
      if (olong == null) {
         return null;
      } else {
         return olong < 0L ? -1L : System.currentTimeMillis() + olong;
      }
   }

   public static boolean isPermanent(String raw) {
      if (raw == null) {
         return true;
      } else {
         String s = raw.trim().toLowerCase(Locale.ROOT);
         return s.isEmpty() || s.equals("perm") || s.equals("permanent") || s.equals("forever") || s.equals("-1");
      }
   }

   public static String formatRemaining(long expiresAt) {
      if (expiresAt <= 0L) {
         return "Permanent";
      } else {
         long i = Math.max(0L, (expiresAt - System.currentTimeMillis()) / 1000L);
         return i <= 0L ? "Expired" : Text.time(i);
      }
   }

   public static String formatExpires(long expiresAt) {
      return expiresAt <= 0L ? "Never" : EXPIRES.format(Instant.ofEpochMilli(expiresAt));
   }

   private static long unitMillis(char unit) {
      return switch (Character.toLowerCase(unit)) {
         case 'd' -> 86400000L;
         case 'h' -> 3600000L;
         case 'm' -> 60000L;
         case 's' -> 1000L;
         case 'w' -> 604800000L;
         default -> 0L;
      };
   }
}
