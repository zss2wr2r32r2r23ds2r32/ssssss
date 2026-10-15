package com.sharded.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextDecoration.State;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Text {
   private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder().character('&').hexColors().build();
   private static final MiniMessage MINI = MiniMessage.miniMessage();

   private Text() {
   }

   public static Component rich(String input) {
      if (input == null || input.isEmpty()) {
         return Component.empty();
      }
      if (input.indexOf('<') >= 0) {
         try {
            return MINI.deserialize(input).decorationIfAbsent(TextDecoration.ITALIC, State.FALSE);
         } catch (Exception ignored) {
         }
      }
      return c(input);
   }

   public static Component c(String input) {
      return (Component)(input != null && !input.isEmpty()
         ? SERIALIZER.deserialize(ColorUtil.normalize(input)).decorationIfAbsent(TextDecoration.ITALIC, State.FALSE)
         : Component.empty());
   }

   public static Component cPlain(String input) {
      return (Component)(input != null && !input.isEmpty()
         ? SERIALIZER.deserialize(ColorUtil.normalizePlain(input)).decorationIfAbsent(TextDecoration.ITALIC, State.FALSE)
         : Component.empty());
   }

   public static String legacySection(String input) {
      return input != null && !input.isEmpty() ? LegacyComponentSerializer.legacySection().serialize(c(input)) : "";
   }

   public static String apply(String input, String... replacements) {
      if (input == null) {
         return "";
      } else {
         String s = input;

         for (int i = 0; i + 1 < replacements.length; i += 2) {
            s = s.replace(replacements[i], replacements[i + 1] == null ? "" : replacements[i + 1]);
         }

         return s;
      }
   }

   public static String time(long seconds) {
      if (seconds <= 0L) {
         return "0s";
      } else {
         long i = seconds / 3600L;
         long j = seconds % 3600L / 60L;
         long k = seconds % 60L;
         StringBuilder stringbuilder = new StringBuilder();
         if (i > 0L) {
            stringbuilder.append(i).append("h ");
         }

         if (j > 0L) {
            stringbuilder.append(j).append("m ");
         }

         if (k > 0L || stringbuilder.isEmpty()) {
            stringbuilder.append(k).append("s");
         }

         return stringbuilder.toString().trim();
      }
   }

   public static String timeWeeksDaysMinutes(long seconds) {
      long remaining = Math.max(0L, seconds);
      long weeks = remaining / 604800L;
      remaining %= 604800L;
      long days = remaining / 86400L;
      remaining %= 86400L;
      long hours = remaining / 3600L;
      remaining %= 3600L;
      long minutes = remaining / 60L;
      if (remaining % 60L > 0L) {
         minutes++;
      }
      if (minutes >= 60L) {
         hours += minutes / 60L;
         minutes %= 60L;
      }
      if (hours >= 24L) {
         days += hours / 24L;
         hours %= 24L;
      }
      if (days >= 7L) {
         weeks += days / 7L;
         days %= 7L;
      }
      StringBuilder out = new StringBuilder();
      if (weeks > 0L) {
         out.append(weeks).append("w ");
      }
      if (days > 0L) {
         out.append(days).append("d ");
      }
      if (hours > 0L) {
         out.append(hours).append("h ");
      }
      if (minutes > 0L || out.isEmpty()) {
         out.append(Math.max(1L, minutes)).append("m");
      }
      return out.toString().trim();
   }

   public static String timeDaysHours(long seconds) {
      if (seconds <= 0L) {
         return "0m";
      } else {
         long i = seconds / 86400L;
         long j = seconds % 86400L / 3600L;
         long k = seconds % 3600L / 60L;
         StringBuilder stringbuilder = new StringBuilder();
         if (i > 0L) {
            stringbuilder.append(i).append("d ");
         }

         if (j > 0L) {
            stringbuilder.append(j).append("h ");
         }

         if (k > 0L && i == 0L) {
            stringbuilder.append(k).append("m ");
         }

         if (stringbuilder.isEmpty()) {
            stringbuilder.append("1m");
         }

         return stringbuilder.toString().trim();
      }
   }

   public static String formatPlaytime(long totalMinutes) {
      if (totalMinutes <= 0L) {
         return "0m";
      } else {
         long i = totalMinutes / 1440L;
         long j = totalMinutes % 1440L / 60L;
         long k = totalMinutes % 60L;
         StringBuilder stringbuilder = new StringBuilder();
         if (i > 0L) {
            stringbuilder.append(i).append("d ");
         }

         if (j > 0L) {
            stringbuilder.append(j).append("h ");
         }

         if (k > 0L || stringbuilder.isEmpty()) {
            stringbuilder.append(k).append("m");
         }

         return stringbuilder.toString().trim();
      }
   }

   public static long ticksToMinutes(long ticks) {
      return Math.max(0L, ticks / 1200L);
   }

   public static String pretty(String key) {
      String[] astring = key.toLowerCase().replace('_', ' ').split(" ");
      StringBuilder stringbuilder = new StringBuilder();

      for (String s : astring) {
         if (!s.isEmpty()) {
            if (!stringbuilder.isEmpty()) {
               stringbuilder.append(' ');
            }

            stringbuilder.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
         }
      }

      return stringbuilder.toString();
   }
}
