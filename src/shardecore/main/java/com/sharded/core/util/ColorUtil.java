package com.sharded.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
   private static final Pattern EXTENDED_HEX = Pattern.compile("(?i)&x((?:&[0-9a-fA-F]){6})");
   private static final Pattern FULL_HEX = Pattern.compile("(?i)&#([0-9a-fA-F]{6})");
   private static final Pattern SHORT_HEX = Pattern.compile("(?i)&#([0-9a-fA-F])(?![0-9a-fA-F])");
   private static final Pattern BARE_HEX = Pattern.compile("(?i)(?<![&])#([0-9a-fA-F]{6})(?=[^0-9a-fA-F]|$)");
   private static final Pattern DOUBLE_AMP = Pattern.compile("&&+");

   private ColorUtil() {
   }

   private static String convertMiniMessageTags(String input) {
      return input.replaceAll("(?i)<bold>", "&l")
         .replaceAll("(?i)</bold>", "&r")
         .replaceAll("(?i)<underlined>", "&n")
         .replaceAll("(?i)</underlined>", "&r")
         .replaceAll("(?i)<u>", "&n")
         .replaceAll("(?i)</u>", "&r")
         .replaceAll("(?i)<italic>", "&o")
         .replaceAll("(?i)</italic>", "&r")
         .replaceAll("(?i)<i>", "&o")
         .replaceAll("(?i)</i>", "&r")
         .replaceAll("(?i)<reset>", "&r")
         .replaceAll("(?i)</reset>", "&r");
   }

   public static String normalize(String input) {
      if (input != null && !input.isEmpty()) {
         String s = input.replace('§', '&');
         s = DOUBLE_AMP.matcher(s).replaceAll("&");
         s = s.replaceAll("&&(?=#)", "&");
         s = convertMiniMessageTags(s);
         s = convertExtendedHex(s);
         s = fixBareHex(s);
         s = fixShortHex(s);
         return uppercaseBoldWords(s);
      } else {
         return "";
      }
   }

   public static String normalizePlain(String input) {
      if (input != null && !input.isEmpty()) {
         String s = input.replace('§', '&');
         s = DOUBLE_AMP.matcher(s).replaceAll("&");
         s = s.replaceAll("&&(?=#)", "&");
         s = convertMiniMessageTags(s);
         s = convertExtendedHex(s);
         s = fixBareHex(s);
         return fixShortHex(s);
      } else {
         return "";
      }
   }

   public static String hexToLegacy(String input) {
      return hexToLegacy(input, true);
   }

   public static String hexToLegacyPlain(String input) {
      return hexToLegacy(input, false);
   }

   private static String hexToLegacy(String input, boolean uppercaseBold) {
      if (input != null && !input.isEmpty()) {
         String s = uppercaseBold ? normalize(input) : normalizePlain(input);
         Matcher matcher = FULL_HEX.matcher(s);
         StringBuilder stringbuilder = new StringBuilder();

         while (matcher.find()) {
            String s1 = matcher.group(1);
            StringBuilder stringbuilder1 = new StringBuilder("&x");

            for (char c0 : s1.toCharArray()) {
               stringbuilder1.append('&').append(c0);
            }

            matcher.appendReplacement(stringbuilder, Matcher.quoteReplacement(stringbuilder1.toString()));
         }

         matcher.appendTail(stringbuilder);
         return stringbuilder.toString();
      } else {
         return "";
      }
   }

   public static String uppercaseBoldWords(String input) {
      if (input != null && !input.isEmpty()) {
         Pattern pattern = Pattern.compile("(?i)&l([A-Za-z][A-Za-z0-9'\\-_ ]*)");
         Matcher matcher = pattern.matcher(input);
         StringBuilder stringbuilder = new StringBuilder();

         while (matcher.find()) {
            matcher.appendReplacement(stringbuilder, Matcher.quoteReplacement("&l" + matcher.group(1).toUpperCase(Locale.ROOT)));
         }

         matcher.appendTail(stringbuilder);
         return stringbuilder.toString();
      } else {
         return "";
      }
   }

   private static String convertExtendedHex(String input) {
      Matcher matcher = EXTENDED_HEX.matcher(input);
      StringBuilder stringbuilder = new StringBuilder();

      while (matcher.find()) {
         String s = matcher.group(1);
         StringBuilder stringbuilder1 = new StringBuilder();

         for (int i = 0; i < s.length(); i += 2) {
            if (s.charAt(i) == '&' && i + 1 < s.length()) {
               stringbuilder1.append(s.charAt(i + 1));
            }
         }

         if (stringbuilder1.length() == 6) {
            matcher.appendReplacement(stringbuilder, Matcher.quoteReplacement("&#" + stringbuilder1));
         } else {
            matcher.appendReplacement(stringbuilder, Matcher.quoteReplacement(matcher.group()));
         }
      }

      matcher.appendTail(stringbuilder);
      return stringbuilder.toString();
   }

   private static String fixBareHex(String input) {
      Matcher matcher = BARE_HEX.matcher(input);
      StringBuilder stringbuilder = new StringBuilder();

      while (matcher.find()) {
         matcher.appendReplacement(stringbuilder, Matcher.quoteReplacement("&#" + matcher.group(1)));
      }

      matcher.appendTail(stringbuilder);
      return stringbuilder.toString();
   }

   private static String fixShortHex(String input) {
      Matcher matcher = FULL_HEX.matcher(input);
      StringBuilder stringbuilder = new StringBuilder();
      List<String> list = new ArrayList<>();

      int i;
      for (i = 0; matcher.find(); i = matcher.end()) {
         stringbuilder.append(input, i, matcher.start());
         list.add(matcher.group());
         stringbuilder.append('\ue000').append(list.size() - 1).append('\ue001');
      }

      stringbuilder.append(input.substring(i));
      String s = SHORT_HEX.matcher(stringbuilder.toString()).replaceAll("&$1");

      for (int j = 0; j < list.size(); j++) {
         s = s.replace("\ue000" + j + "\ue001", list.get(j));
      }

      return s;
   }
}
