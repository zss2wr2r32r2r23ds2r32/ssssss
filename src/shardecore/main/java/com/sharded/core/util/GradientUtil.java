package com.sharded.core.util;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GradientUtil {
   private static final Pattern HEX = Pattern.compile("(?i)#?([0-9a-f]{6})");

   private GradientUtil() {
   }

   public static String apply(String text, String fromHex, String toHex) {
      return apply(text, fromHex, toHex);
   }

   public static String apply(String text, String... stops) {
      if (text != null && !text.isEmpty()) {
         if (stops != null && stops.length != 0) {
            List<Color> list = new ArrayList<>();

            for (String s : stops) {
               Color color = parseHex(s);
               if (color != null) {
                  list.add(color);
               }
            }

            if (list.isEmpty()) {
               return text;
            } else if (list.size() == 1) {
               return String.format(Locale.US, "&#%02X%02X%02X", list.get(0).getRed(), list.get(0).getGreen(), list.get(0).getBlue()) + stripLegacy(text);
            } else {
               String s1 = stripLegacy(text);
               if (s1.isEmpty()) {
                  return text;
               } else {
                  StringBuilder stringbuilder = new StringBuilder();
                  int i = s1.length();

                  for (int j = 0; j < i; j++) {
                     double d0 = i == 1 ? 0.0 : (double)j / (double)(i - 1);
                     Color color1 = mix(list, d0);
                     stringbuilder.append(String.format(Locale.US, "&#%02X%02X%02X", color1.getRed(), color1.getGreen(), color1.getBlue()));
                     stringbuilder.append(s1.charAt(j));
                  }

                  return stringbuilder.toString();
               }
            }
         } else {
            return text;
         }
      } else {
         return text == null ? "" : text;
      }
   }

   public static boolean isGradient(String spec) {
      String[] astring = splitGradient(spec);
      return astring != null && astring.length >= 2;
   }

   public static String[] splitGradient(String spec) {
      if (spec != null && !spec.isBlank()) {
         String[] astring = spec.trim().split("\\s+");
         List<String> list = new ArrayList<>();

         for (String s : astring) {
            Color color = parseHex(s);
            if (color == null) {
               return null;
            }

            list.add(toHex(color));
         }

         return list.size() < 2 ? null : list.toArray(String[]::new);
      } else {
         return null;
      }
   }

   private static Color mix(List<Color> colors, double t) {
      if (colors.size() == 1) {
         return colors.get(0);
      } else {
         double d0 = Math.max(0.0, Math.min(1.0, t)) * (double)(colors.size() - 1);
         int i = Math.min(colors.size() - 2, (int)Math.floor(d0));
         double d1 = d0 - (double)i;
         Color color = colors.get(i);
         Color color1 = colors.get(i + 1);
         int j = (int)Math.round((double)color.getRed() + d1 * (double)(color1.getRed() - color.getRed()));
         int k = (int)Math.round((double)color.getGreen() + d1 * (double)(color1.getGreen() - color.getGreen()));
         int l = (int)Math.round((double)color.getBlue() + d1 * (double)(color1.getBlue() - color.getBlue()));
         return new Color(clamp(j), clamp(k), clamp(l));
      }
   }

   private static int clamp(int value) {
      return Math.max(0, Math.min(255, value));
   }

   private static Color parseHex(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.trim().replace("&#", "#").replace("&", "");
         Matcher matcher = HEX.matcher(s);
         if (!matcher.find()) {
            return null;
         } else {
            try {
               int i = Integer.parseInt(matcher.group(1), 16);
               return new Color(i >> 16 & 0xFF, i >> 8 & 0xFF, i & 0xFF);
            } catch (NumberFormatException numberformatexception) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static String toHex(Color color) {
      return String.format(Locale.ROOT, "#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
   }

   private static String stripLegacy(String input) {
      if (input != null && !input.isEmpty()) {
         StringBuilder stringbuilder = new StringBuilder(input.length());

         for (int i = 0; i < input.length(); i++) {
            char c0 = input.charAt(i);
            if ((c0 == 167 || c0 == '&') && i + 1 < input.length()) {
               char c1 = input.charAt(i + 1);
               if ((c1 == '#' || c1 == 'x' || c1 == 'X') && c1 == '#') {
                  i += Math.min(7, input.length() - i - 1);
                  continue;
               }

               if (isLegacyCode(c1)) {
                  i++;
                  continue;
               }
            }

            stringbuilder.append(c0);
         }

         return stringbuilder.toString();
      } else {
         return "";
      }
   }

   private static boolean isLegacyCode(char c) {
      return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c) >= 0;
   }
}
