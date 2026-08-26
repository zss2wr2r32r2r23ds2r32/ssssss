package com.shardedcore.util;

import java.text.DecimalFormat;
import java.util.Locale;

public final class Numbers {

    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.##");

    private Numbers() {
    }

    public static double parse(String input) throws NumberFormatException {
        if (input == null || input.isBlank()) {
            throw new NumberFormatException("Empty number");
        }
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
        }
        return Double.parseDouble(normalized) * multiplier;
    }

    public static String formatShorthand(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000D) {
            return trimTrailingZero(value / 1_000_000_000D) + "b";
        }
        if (abs >= 1_000_000D) {
            return trimTrailingZero(value / 1_000_000D) + "m";
        }
        if (abs >= 1_000D) {
            return trimTrailingZero(value / 1_000D) + "k";
        }
        return FORMAT.format(value);
    }

    private static String trimTrailingZero(double value) {
        String text = String.format(Locale.US, "%.2f", value);
        if (text.endsWith(".00")) {
            return text.substring(0, text.length() - 3);
        }
        if (text.endsWith("0")) {
            return text.substring(0, text.length() - 1);
        }
        return text;
    }

    public static long parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(0L, (long) parse(raw));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public static String format(long value) {
        return formatShorthand(value);
    }
}
