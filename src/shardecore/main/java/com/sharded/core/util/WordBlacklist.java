package com.sharded.core.util;

import java.util.Locale;
import org.bukkit.configuration.file.YamlConfiguration;

public final class WordBlacklist {
   private WordBlacklist() {
   }

   public static boolean contains(YamlConfiguration config, String listKey, String input) {
      if (input != null && !input.isBlank()) {
         String s = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");

         for (String s1 : config.getStringList(listKey)) {
            if (!s1.isBlank() && s.contains(s1.toLowerCase(Locale.ROOT).trim())) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }
}
