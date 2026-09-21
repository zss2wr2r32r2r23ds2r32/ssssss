package com.sharded.core.setup.util;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NumberUtil {

    private static final Pattern SHORT = Pattern.compile("(?i)^([+-]?\\d+(?:\\.\\d+)?)([kmbtq])?$");
    private static final DecimalFormat COMMA = new DecimalFormat("#,##0.##");

    private static final long THOUSAND = 1_000L;
    private static final long MILLION = 1_000_000L;
    private static final long BILLION = 1_000_000_000L;
    private static final long TRILLION = 1_000_000_000_000L;
    private static final long QUADRILLION = 1_000_000_000_000_000L;

    private NumberUtil() {
    }

    public static double parseAmount(String input) {
        if (input == null || input.isBlank()) {
            throw new NumberFormatException("Empty amount");
        }
        String trimmed = input.trim().replace(",", "");
        Matcher matcher = SHORT.matcher(trimmed);
        if (!matcher.matches()) {
            return Double.parseDouble(trimmed);
        }
        double value = Double.parseDouble(matcher.group(1));
        String suffix = matcher.group(2);
        if (suffix == null) {
            return value;
        }
        return switch (suffix.toLowerCase(Locale.ROOT)) {
            case "k" -> value * THOUSAND;
            case "m" -> value * MILLION;
            case "b" -> value * BILLION;
            case "t" -> value * TRILLION;
            case "q" -> value * QUADRILLION;
            default -> value;
        };
    }

    public static String formatComma(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        if (Math.abs(value - Math.rint(value)) < 0.001D) {
            return COMMA.format((long) Math.rint(value));
        }
        return COMMA.format(value);
    }

    public static String formatComma(long value) {
        return COMMA.format(value);
    }

    public static String formatShort(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        return formatShort(Math.round(value));
    }

    public static String formatShort(long value) {
        if (value == 0) {
            return "0";
        }
        boolean negative = value < 0;
        long abs = Math.abs(value);
        String sign = negative ? "-" : "";

        if (abs >= QUADRILLION) {
            return sign + trimScaled(abs, QUADRILLION) + "q";
        }
        if (abs >= TRILLION) {
            return sign + trimScaled(abs, TRILLION) + "t";
        }
        if (abs >= BILLION) {
            double scaled = abs / (double) BILLION;
            if (scaled >= 1000D) {
                return sign + trimScaled(abs, TRILLION) + "t";
            }
            return sign + trimSuffix(scaled) + "b";
        }
        if (abs >= MILLION) {
            double scaled = abs / (double) MILLION;
            if (scaled >= 1000D) {
                return sign + trimScaled(abs, BILLION) + "b";
            }
            return sign + trimSuffix(scaled) + "m";
        }
        if (abs >= THOUSAND) {
            double scaled = abs / (double) THOUSAND;
            if (scaled >= 1000D) {
                return sign + trimScaled(abs, MILLION) + "m";
            }
            return sign + trimSuffix(scaled) + "k";
        }
        return formatComma(value);
    }

    private static String trimScaled(long abs, long unit) {
        return trimSuffix(abs / (double) unit);
    }

    private static String trimSuffix(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.1f", value).replace(".0", "");
    }
}
