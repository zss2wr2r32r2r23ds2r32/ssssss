package com.sharded.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

public final class ColorConfigUtil {
   private static final Pattern HEX_IN_COMMAND = Pattern.compile("#([0-9A-Fa-f]{6})");

   private ColorConfigUtil() {
   }

   public static String resolveValue(ConfigurationSection section, String defaultValue) {
      if (section == null) {
         return defaultValue;
      } else {
         String s = section.getString("value");
         if (s != null && !s.isBlank() && !looksLikeLegacyCommand(s)) {
            return s.trim();
         } else {
            String s1 = section.getString("command");
            if (s1 != null && !s1.isBlank()) {
               String s2 = parseLegacyCommand(s1);
               if (s2 != null) {
                  return s2;
               }
            }

            return defaultValue;
         }
      }
   }

   public static String resolvePermission(String id, ConfigurationSection section, String prefix) {
      String s = section.getString("permission", prefix + id);
      if (s.startsWith("namecolor.set.color.")) {
         return "sharded.namecolor." + id;
      } else if (s.startsWith("ezcolor.color.")) {
         return "sharded.chatcolor." + id;
      } else {
         return s.startsWith("eternaltags.tag.") ? "sharded.tag." + id : s;
      }
   }

   private static boolean looksLikeLegacyCommand(String value) {
      String s = value.toLowerCase();
      return s.contains("namecolor:") || s.startsWith("ezcolor") || s.contains("eternaltags");
   }

   private static String parseLegacyCommand(String command) {
      String s = command.toLowerCase();
      if (s.contains("rainbow")) {
         return "rainbow";
      } else {
         Matcher matcher = HEX_IN_COMMAND.matcher(command);
         return matcher.find() ? "&#" + matcher.group(1) : null;
      }
   }
}
