package com.sharded.core.modules.chatfilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WordRule {
   private final String name;
   private final WordRule.Action action;
   private final List<String> words;
   private final List<String> regex;

   public WordRule(String name, WordRule.Action action, List<String> words, List<String> regex) {
      this.name = name;
      this.action = action;
      this.words = new ArrayList<>(words);
      this.regex = new ArrayList<>(regex);
   }

   public String name() {
      return this.name;
   }

   public WordRule.Action action() {
      return this.action;
   }

   public List<String> words() {
      return this.words;
   }

   public List<String> regex() {
      return this.regex;
   }

   public boolean containsWord(String word) {
      String s = word.toLowerCase(Locale.ROOT);

      for (String s1 : this.words) {
         if (s1.equalsIgnoreCase(s)) {
            return true;
         }
      }

      for (String s2 : this.regex) {
         if (s2.equalsIgnoreCase(s) || s2.equalsIgnoreCase(word)) {
            return true;
         }
      }

      return false;
   }

   public static enum Action {
      MASK,
      CANCEL,
      WARN;

      public static WordRule.Action parse(String raw) {
         if (raw == null) {
            return CANCEL;
         } else {
            String s = raw.toUpperCase(Locale.ROOT);

            return switch (s) {
               case "MASK", "REPLACE" -> MASK;
               case "WARN" -> WARN;
               default -> CANCEL;
            };
         }
      }
   }
}
