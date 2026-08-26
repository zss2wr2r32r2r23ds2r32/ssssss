package com.shardedcore.util;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        if (abs >= 1_000_000_000_000D) return trim(value / 1_000_000_000_000D) + "T";
        if (abs >= 1_000_000_000D) return trim(value / 1_000_000_000D) + "B";
        if (abs >= 1_000_000D) return trim(value / 1_000_000D) + "M";
        if (abs >= 1_000D) return trim(value / 1_000D) + "K";
        return COMMA.format(value);
    }

    public static String commas(double value) {
        return COMMA.format(value);
    }

    private static final Pattern DURATION = Pattern.compile("(\\d+)([dhms])");

    /** 30m, 1h, 1d12h, 1h30m, or a bare number treated as minutes. */
    public static long durationMillis(String input) {
        if (input == null || input.isBlank()) return 0L;
        String raw = input.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (raw.chars().allMatch(Character::isDigit)) {
            try {
                return Long.parseLong(raw) * 60_000L;
            } catch (NumberFormatException ex) {
                return 0L;
            }
        }
        Matcher matcher = DURATION.matcher(raw);
        long total = 0L;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            long amount = Long.parseLong(matcher.group(1));
            total += switch (matcher.group(2)) {
                case "d" -> amount * 86_400_000L;
                case "h" -> amount * 3_600_000L;
                case "m" -> amount * 60_000L;
                default -> amount * 1_000L;
            };
        }
        return found ? total : 0L;
    }

    public static String duration(long millis, String day, String hour, String minute, String second, int units) {
        if (millis <= 0L) return "0" + second;
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        List<String> parts = new ArrayList<>();
        if (days > 0) parts.add(days + day);
        if (hours > 0) parts.add(hours + hour);
        if (minutes > 0) parts.add(minutes + minute);
        if (seconds > 0 && parts.size() < Math.max(1, units)) parts.add(seconds + second);
        if (parts.isEmpty()) parts.add("0" + second);
        int limit = Math.max(1, units);
        if (parts.size() > limit) parts = parts.subList(0, limit);
        return String.join(" ", parts);
    }

    private static String trim(double value) {
        String text = String.format(Locale.US, "%.2f", value);
        if (text.endsWith(".00")) return text.substring(0, text.length() - 3);
        if (text.endsWith("0")) return text.substring(0, text.length() - 1);
        return text;
    }
}
