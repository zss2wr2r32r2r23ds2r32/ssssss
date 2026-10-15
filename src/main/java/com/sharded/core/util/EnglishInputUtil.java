package com.sharded.core.util;

/** Validates player-facing text as plain English (ASCII letters). */
public final class EnglishInputUtil {

    private EnglishInputUtil() {
    }

    public static boolean isEnglishLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** True when every letter in {@code text} is an ASCII A–Z character. */
    public static boolean isEnglishLettersOnly(String text) {
        if (text == null || text.isEmpty()) return true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c) && !isEnglishLetter(c)) return false;
        }
        return true;
    }

    public static int countEnglishLetters(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isEnglishLetter(text.charAt(i))) count++;
        }
        return count;
    }
}
