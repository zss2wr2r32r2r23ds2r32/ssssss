package com.sharded.core.util;

/** Formats milliseconds as hours, minutes, seconds placeholders. */
public final class TimeFormat {

    private TimeFormat() {
    }

    public static String hms(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours + "h " + minutes + "m " + seconds + "s";
    }

    public static String replacePlaceholders(String input, long millis) {
        if (input == null) return "";
        long totalSeconds = Math.max(0, millis / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return input
                .replace("%hours%", String.valueOf(hours))
                .replace("%minutes%", String.valueOf(minutes))
                .replace("%seconds%", String.valueOf(seconds))
                .replace("%time%", hms(millis));
    }
}
