package com.sharded.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TagDisplayUtil {
   private static final Pattern HEX_COLOR = Pattern.compile("(&x(?:&[0-9A-Fa-f]){6}|&#[0-9A-Fa-f]{6}|&[0-9a-f])", 2);
   private static final Pattern TRAILING_TAG_WORD = Pattern.compile("\\s+tag\\s*$", 2);

   private TagDisplayUtil() {
   }

   public static String tabTag(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = ColorUtil.normalize(raw.trim());
         s = TRAILING_TAG_WORD.matcher(s).replaceAll("");
         return s.trim();
      } else {
         return "";
      }
   }

   public static String accentColor(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = ColorUtil.normalize(raw);
         Matcher matcher = HEX_COLOR.matcher(s);

         while (matcher.find()) {
            String s1 = matcher.group(1);
            if (!s1.equalsIgnoreCase("&8") && !s1.equalsIgnoreCase("&7")) {
               if (s1.startsWith("&x") || s1.startsWith("&#")) {
                  return s1;
               }

               if (s1.length() == 2 && "0123456789abcdef".indexOf(Character.toLowerCase(s1.charAt(1))) >= 0) {
                  return s1;
               }
            }
         }

         return "&x&F&F&B&A&0&0";
      } else {
         return "&x&F&F&B&A&0&0";
      }
   }

   public static String loreLine(String accent, String text) {
      return accent + text;
   }
}
