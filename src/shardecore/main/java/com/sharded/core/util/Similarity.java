package com.sharded.core.util;

import java.util.HashMap;
import java.util.Map.Entry;

public final class Similarity {
   private Similarity() {
   }

   public static int percent(String left, String right) {
      if (left != null && right != null) {
         String s = normalize(left);
         String s1 = normalize(right);
         if (s.isEmpty() && s1.isEmpty()) {
            return 100;
         } else if (s.equals(s1)) {
            return 100;
         } else {
            return !s.isEmpty() && !s1.isEmpty() ? Math.max(dicePercent(s, s1), levenshteinPercent(s, s1)) : 0;
         }
      } else {
         return 0;
      }
   }

   public static String normalize(String text) {
      StringBuilder stringbuilder = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c0 = text.charAt(i);
         if (Character.isLetterOrDigit(c0)) {
            stringbuilder.append(Character.toLowerCase(c0));
         } else if (c0 == ' ' && (stringbuilder.isEmpty() || stringbuilder.charAt(stringbuilder.length() - 1) != ' ')) {
            stringbuilder.append(' ');
         }
      }

      return stringbuilder.toString().trim();
   }

   public static int uniqueLetterCount(String word) {
      boolean[] aboolean = new boolean[32];
      int i = 0;

      for (int j = 0; j < word.length(); j++) {
         char c0 = Character.toLowerCase(word.charAt(j));
         if (c0 >= 'a' && c0 <= 'z') {
            int k = c0 - 'a';
            if (!aboolean[k]) {
               aboolean[k] = true;
               i++;
            }
         }
      }

      return i;
   }

   public static int longestSameInARow(String text) {
      if (text != null && !text.isEmpty()) {
         int i = 1;
         int j = 1;

         for (int k = 1; k < text.length(); k++) {
            if (text.charAt(k) == text.charAt(k - 1)) {
               if (++j > i) {
                  i = j;
               }
            } else {
               j = 1;
            }
         }

         return i;
      } else {
         return 0;
      }
   }

   public static int uppercaseCount(String text) {
      int i = 0;

      for (int j = 0; j < text.length(); j++) {
         char c0 = text.charAt(j);
         if (c0 >= 'A' && c0 <= 'Z') {
            i++;
         }
      }

      return i;
   }

   public static String antiBypassRegex(String word) {
      StringBuilder stringbuilder = new StringBuilder("(?i)");
      boolean flag = true;

      for (int i = 0; i < word.length(); i++) {
         char c0 = word.charAt(i);
         if (Character.isLetterOrDigit(c0)) {
            if (!flag) {
               stringbuilder.append("[\\W_]*");
            }

            flag = false;
            stringbuilder.append(leetClass(c0));
         }
      }

      return stringbuilder.toString();
   }

   private static String leetClass(char raw) {
      char c0 = Character.toLowerCase(raw);

      return switch (c0) {
         case 'a' -> "[a4@]";
         case 'b' -> "[b8]";
         default -> "[" + Character.toLowerCase(c0) + Character.toUpperCase(c0) + "]";
         case 'e' -> "[e3]";
         case 'g' -> "[g6]";
         case 'i' -> "[i1!l]";
         case 'l' -> "[l1!]";
         case 'o' -> "[o0]";
         case 's' -> "[s5$]";
         case 't' -> "[t7]";
      };
   }

   private static int dicePercent(String a, String b) {
      if (a.length() >= 2 && b.length() >= 2) {
         HashMap<String, Integer> hashmap = bigrams(a);
         HashMap<String, Integer> hashmap1 = bigrams(b);
         int i = 0;
         int j = 0;

         for (Entry<String, Integer> entry : hashmap.entrySet()) {
            j += entry.getValue();
            i += Math.min(entry.getValue(), hashmap1.getOrDefault(entry.getKey(), 0));
         }

         for (Entry<String, Integer> entry1 : hashmap1.entrySet()) {
            j += entry1.getValue();
         }

         return j == 0 ? 0 : (int)Math.round(2.0 * (double)i * 100.0 / (double)j);
      } else {
         return a.equals(b) ? 100 : 0;
      }
   }

   private static HashMap<String, Integer> bigrams(String text) {
      HashMap<String, Integer> hashmap = new HashMap<>();

      for (int i = 0; i < text.length() - 1; i++) {
         String s = text.substring(i, i + 2);
         hashmap.merge(s, 1, Integer::sum);
      }

      return hashmap;
   }

   private static int levenshteinPercent(String a, String b) {
      int i = levenshtein(a, b);
      int j = Math.max(a.length(), b.length());
      return j == 0 ? 100 : (int)Math.round((1.0 - (double)i / (double)j) * 100.0);
   }

   private static int levenshtein(String a, String b) {
      int i = a.length();
      int j = b.length();
      int[] aint = new int[j + 1];
      int[] aint1 = new int[j + 1];
      int k = 0;

      while (k <= j) {
         aint[k] = k++;
      }

      for (int j1 = 1; j1 <= i; j1++) {
         aint1[0] = j1;
         char c0 = a.charAt(j1 - 1);

         for (int l = 1; l <= j; l++) {
            int i1 = c0 == b.charAt(l - 1) ? 0 : 1;
            aint1[l] = Math.min(Math.min(aint1[l - 1] + 1, aint[l] + 1), aint[l - 1] + i1);
         }

         int[] aint2 = aint;
         aint = aint1;
         aint1 = aint2;
      }

      return aint[j];
   }
}
