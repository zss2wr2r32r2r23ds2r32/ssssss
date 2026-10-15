package com.sharded.core.util;

import java.text.DecimalFormat;
import java.util.Locale;

public final class Numbers {
   private static final DecimalFormat WHOLE = new DecimalFormat("#,##0");

   private Numbers() {
   }

   public static String format(long value) {
      if (value >= 1000000000L) {
         return trim((double)value / 1.0E9) + "B";
      } else if (value >= 1000000L) {
         return trim((double)value / 1000000.0) + "M";
      } else {
         return value >= 1000L ? trim((double)value / 1000.0) + "K" : WHOLE.format(value);
      }
   }

   public static long parseAmount(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.trim().replace(",", "").replace("_", "");
         String s1 = s.toLowerCase(Locale.ROOT);

         try {
            if (s1.endsWith("b")) {
               return Math.max(0L, (long)(Double.parseDouble(s1.substring(0, s1.length() - 1)) * 1.0E9));
            } else if (s1.endsWith("m")) {
               return Math.max(0L, (long)(Double.parseDouble(s1.substring(0, s1.length() - 1)) * 1000000.0));
            } else if (s1.endsWith("k")) {
               return Math.max(0L, (long)(Double.parseDouble(s1.substring(0, s1.length() - 1)) * 1000.0));
            } else {
               return s1.contains(".") ? Math.max(0L, (long)Double.parseDouble(s1)) : Math.max(0L, Long.parseLong(s1));
            }
         } catch (NumberFormatException numberformatexception) {
            return 0L;
         }
      } else {
         return 0L;
      }
   }

   private static String trim(double value) {
      return value % 1.0 == 0.0 ? String.valueOf((long)value) : String.format("%.1f", value);
   }
}
