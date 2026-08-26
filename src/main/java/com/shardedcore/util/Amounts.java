package com.shardedcore.util;

import java.text.DecimalFormat;
import java.util.Locale;

public final class Amounts {

    private static final DecimalFormat COMMA = new DecimalFormat("#,##0.##");

    private Amounts() {
    }

    public static double parse(String input) {
        if (input == null || input.isBlank()) return 0;
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace(",", "");
        double multiplier = 1.0D;
        if (normalized.endsWith("k")) {
            multiplier = 1_000D;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("m")) {
            multiplier = 1_000_000D;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("b")) {
            multiplier = 1_000_000_000D;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("t")) {
            multiplier = 1_000_000_000_000D;
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            return Double.parseDouble(normalized) * multiplier;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public static long parseLong(String input) {
        return Math.max(0L, (long) parse(input));
    }

    public static String format(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000_000D) return trim(value / 1_000_000_000_000D) + "t";
        if (abs >= 1_000_000_000D) return trim(value / 1_000_000_000D) + "b";
        if (abs >= 1_000_000D) return trim(value / 1_000_000D) + "m";
        if (abs >= 1_000D) return trim(value / 1_000D) + "k";
        return COMMA.format(value);
    }

    public static String commas(double value) {
        return COMMA.format(value);
    }

    private static String trim(double value) {
        String text = String.format(Locale.US, "%.2f", value);
        if (text.endsWith(".00")) return text.substring(0, text.length() - 3);
        if (text.endsWith("0")) return text.substring(0, text.length() - 1);
        return text;
    }
}
