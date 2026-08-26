package com.sharded.core.util;

import java.awt.Color;
import java.util.Locale;

/** Builds legacy gradient strings from two hex colours. */
public final class GradientUtil {

    private GradientUtil() {
    }

    public static String apply(String text, String startHex, String endHex) {
        if (text == null || text.isEmpty()) return "";
        Color start = parseHex(startHex);
        Color end = parseHex(endHex);
        if (start == null || end == null) return text;
        String plain = stripLegacy(text);
        if (plain.isEmpty()) return text;
        StringBuilder out = new StringBuilder();
        int len = plain.length();
        for (int i = 0; i < len; i++) {
            double ratio = len <= 1 ? 0 : (double) i / (len - 1);
            int r = (int) (start.getRed() + ratio * (end.getRed() - start.getRed()));
            int g = (int) (start.getGreen() + ratio * (end.getGreen() - start.getGreen()));
            int b = (int) (start.getBlue() + ratio * (end.getBlue() - start.getBlue()));
            out.append(String.format(Locale.US, "&#%02X%02X%02X", r, g, b)).append(plain.charAt(i));
        }
        return out.toString();
    }

    /** {@code #FF005D #FFFFFF} or {@code FF005D FFFFFF} */
    public static boolean isGradient(String input) {
        if (input == null) return false;
        String[] parts = input.trim().split("\\s+");
        return parts.length == 2 && parseHex(parts[0]) != null && parseHex(parts[1]) != null;
    }

    public static String[] splitGradient(String input) {
        String[] parts = input.trim().split("\\s+");
        if (parts.length != 2) return null;
        Color a = parseHex(parts[0]);
        Color b = parseHex(parts[1]);
        if (a == null || b == null) return null;
        return new String[] { toHex(a), toHex(b) };
    }

    private static Color parseHex(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.startsWith("&#")) s = s.substring(2);
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return null;
        try {
            return Color.decode("#" + s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String toHex(Color c) {
        return String.format(Locale.US, "#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String stripLegacy(String input) {
        return ColorUtil.normalize(input)
                .replaceAll("(?i)&#[0-9a-f]{6}", "")
                .replaceAll("(?i)&[0-9a-fk-or]", "")
                .replace("§", "");
    }
}
