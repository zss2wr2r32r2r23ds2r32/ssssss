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

    private static String trim(double value) {
        return value % 1.0 == 0 ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}
