package com.sharded.core.util;

import java.text.DecimalFormat;

public final class Numbers {

    private static final DecimalFormat WHOLE = new DecimalFormat("#,##0");

    private Numbers() {
    }

    public static String format(long value) {
        if (value >= 1_000_000_000L) return trim(value / 1_000_000_000.0) + "B";
        if (value >= 1_000_000L) return trim(value / 1_000_000.0) + "M";
        if (value >= 1_000L) return trim(value / 1_000.0) + "K";
        return WHOLE.format(value);
    }

    /** Parses plain numbers and suffixes like 1k, 1.5m, 2b. Returns 0 on failure. */
    public static long parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        String cleaned = raw.trim().replace(",", "").replace("_", "");
        String lower = cleaned.toLowerCase(java.util.Locale.ROOT);
        try {
            if (lower.endsWith("b")) {
                return Math.max(0L, (long) (Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1_000_000_000L));
            }
            if (lower.endsWith("m")) {
                return Math.max(0L, (long) (Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1_000_000L));
            }
            if (lower.endsWith("k")) {
                return Math.max(0L, (long) (Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1_000L));
            }
            if (lower.contains(".")) {
                return Math.max(0L, (long) Double.parseDouble(lower));
            }
            return Math.max(0L, Long.parseLong(lower));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String trim(double value) {
        return value % 1.0 == 0 ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}
