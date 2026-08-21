package com.sharded.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Text {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    /** Deserializes color codes including &amp;x&amp;R&amp;R&amp;G&amp;G&amp;B&amp;B and &amp;#rrggbb hex. */
    public static Component c(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        return SERIALIZER.deserialize(ColorUtil.normalize(input)).decorationIfAbsent(
                net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }

    /** Replaces %placeholders% in pairs: apply("hi %name%", "%name%", "Bob"). */
    public static String apply(String input, String... replacements) {
        if (input == null) return "";
        String out = input;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            out = out.replace(replacements[i], replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        return out;
    }

    /** Formats seconds as "1h 23m 45s". */
    public static String time(long seconds) {
        if (seconds <= 0) return "0s";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.isEmpty()) sb.append(s).append("s");
        return sb.toString().trim();
    }

    /** Formats seconds as "2d 5h" or "3h 20m" — days first when >= 1 day. */
    public static String timeDaysHours(long seconds) {
        if (seconds <= 0) return "0m";
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0 && d == 0) sb.append(m).append("m ");
        if (sb.isEmpty()) sb.append("1m");
        return sb.toString().trim();
    }

    /** Formats minutes as "2d 5h 13m". */
    public static String formatPlaytime(long totalMinutes) {
        if (totalMinutes <= 0) return "0m";
        long days = totalMinutes / 1440;
        long hours = (totalMinutes % 1440) / 60;
        long mins = totalMinutes % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0 || sb.isEmpty()) sb.append(mins).append("m");
        return sb.toString().trim();
    }

    /** Converts Minecraft PLAY_ONE_MINUTE statistic ticks to whole minutes. */
    public static long ticksToMinutes(long ticks) {
        return Math.max(0L, ticks / 1200L);
    }

    /** Pretty name for an enum-like key: "iron_ingot" -> "Iron Ingot". */
    public static String pretty(String key) {
        String[] parts = key.toLowerCase().replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
