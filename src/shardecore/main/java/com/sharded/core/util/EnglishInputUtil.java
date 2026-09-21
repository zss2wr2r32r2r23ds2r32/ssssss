package com.sharded.core.util;

public final class EnglishInputUtil {
   private EnglishInputUtil() {
   }

   public static boolean isEnglishLetter(char c) {
      return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
   }

   public static boolean isEnglishLettersOnly(String text) {
      if (text != null && !text.isEmpty()) {
         for (int i = 0; i < text.length(); i++) {
            char c0 = text.charAt(i);
            if (Character.isLetter(c0) && !isEnglishLetter(c0)) {
               return false;
            }
         }

         return true;
      } else {
         return true;
      }
   }

   public static int countEnglishLetters(String text) {
      if (text != null && !text.isEmpty()) {
         int i = 0;

         for (int j = 0; j < text.length(); j++) {
            if (isEnglishLetter(text.charAt(j))) {
               i++;
            }
         }

         return i;
      } else {
         return 0;
      }
   }
}
