package com.sharded.core.util;

public final class RainbowUtil {
   private static final String[] RAINBOW = new String[]{"&#FF0000", "&#FF7F00", "&#FFFF00", "&#00FF00", "&#0000FF", "&#4B0082", "&#9400D3"};

   private RainbowUtil() {
   }

   public static String apply(String text) {
      if (text != null && !text.isEmpty()) {
         String s = ColorUtil.normalize(text).replaceAll("(?i)&#[0-9a-f]{6}", "").replaceAll("(?i)&[0-9a-fk-or]", "").replace("§", "");
         if (s.isEmpty()) {
            return text;
         } else {
            StringBuilder stringbuilder = new StringBuilder();
            int i = 0;

            for (int j = 0; j < s.length(); j++) {
               char c0 = s.charAt(j);
               if (c0 == ' ') {
                  stringbuilder.append(' ');
               } else {
                  stringbuilder.append(RAINBOW[i % RAINBOW.length]).append(c0);
                  i++;
               }
            }

            return stringbuilder.toString();
         }
      } else {
         return "";
      }
   }
}
