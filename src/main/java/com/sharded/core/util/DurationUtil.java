package com.sharded.core.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses punishment durations like 7d, 1h, 30m, permanent. */
public final class DurationUtil {

    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter EXPIRES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private DurationUtil() {
    }

    /** Milliseconds from now, or {@code null} for permanent. {@code -1} if invalid. */
    public static Long parseToMillis(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (isPermanent(trimmed)) return null;

        Matcher matcher = PART.matcher(trimmed.toLowerCase(Locale.ROOT));
        long total = 0;
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            long amount = Long.parseLong(matcher.group(1));
            total += amount * unitMillis(matcher.group(2).charAt(0));
        }
        if (!matched) {
            try {
                long seconds = Long.parseLong(trimmed);
                return seconds <= 0 ? null : seconds * 1000L;
            } catch (NumberFormatException e) {
                return -1L;
            }
        }
        return total <= 0 ? -1L : total;
    }

    public static Long expiresAt(String raw) {
        Long millis = parseToMillis(raw);
        if (millis == null) return null;
        if (millis < 0) return -1L;
        return System.currentTimeMillis() + millis;
    }

    public static boolean isPermanent(String raw) {
        if (raw == null) return true;
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        return lower.isEmpty()
                || lower.equals("perm")
                || lower.equals("permanent")
                || lower.equals("forever")
                || lower.equals("-1");
    }

    public static String formatRemaining(long expiresAt) {
        if (expiresAt <= 0) return "Permanent";
        long seconds = Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000L);
        if (seconds <= 0) return "Expired";
        return Text.time(seconds);
    }

    public static String formatExpires(long expiresAt) {
        if (expiresAt <= 0) return "Never";
        return EXPIRES.format(Instant.ofEpochMilli(expiresAt));
    }

    private static long unitMillis(char unit) {
        return switch (Character.toLowerCase(unit)) {
            case 's' -> 1_000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            case 'w' -> 604_800_000L;
            default -> 0L;
        };
    }
}
