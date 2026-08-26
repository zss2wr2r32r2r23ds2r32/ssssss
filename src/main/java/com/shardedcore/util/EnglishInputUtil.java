package com.shardedcore.util;

public final class EnglishInputUtil {

    private EnglishInputUtil() {}

    public static boolean isEnglishLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    public static boolean isEnglishLettersOnly(String text) {
        if (text == null || text.isEmpty()) return true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c) && !isEnglishLetter(c)) return false;
        }
        return true;
    }
}
