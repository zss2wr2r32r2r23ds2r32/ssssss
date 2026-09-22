package com.sharded.core.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaytimeParser {
   private static final Pattern PART = Pattern.compile("(\\d+)([dhms])", 2);

   private PlaytimeParser() {
   }

   public static long parseMinutes(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.trim().toLowerCase(Locale.ROOT).replace(" ", "");
         if (s.matches("\\d+")) {
            return Long.parseLong(s);
         } else {
            long i = 0L;
            Matcher matcher = PART.matcher(s);
            boolean flag = false;

            while (matcher.find()) {
               flag = true;
               long j = Long.parseLong(matcher.group(1));

               i += switch (matcher.group(2).charAt(0)) {
                  case 'd' -> j * 1440L;
                  case 'h' -> j * 60L;
                  case 'm' -> j;
                  case 's' -> Math.max(0L, j / 60L);
                  default -> 0L;
               };
            }

            return flag ? i : 0L;
         }
      } else {
         return 0L;
      }
   }
}
