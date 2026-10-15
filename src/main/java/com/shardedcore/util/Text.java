package com.shardedcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Text {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    public static Component c(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        return SERIALIZER.deserialize(ColorUtil.hexToLegacy(input)).decorationIfAbsent(
                net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }

    public static Component cPlain(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        return SERIALIZER.deserialize(ColorUtil.hexToLegacyPlain(input)).decorationIfAbsent(
                net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }

    public static Component component(String input) {
        return c(input);
    }

    public static Component component(String input, Player player) {
        return c(applyPlaceholders(input, player));
    }

    public static String legacySection(String input) {
        if (input == null || input.isEmpty()) return "";
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(c(input));
    }

    public static String applyPlaceholders(String input, Player player) {
        if (input == null) return "";
        if (player != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                return (String) papi.getMethod("setPlaceholders", Player.class, String.class)
                        .invoke(null, player, input);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return input;
    }

    public static String applyPlaceholders(String input, Player player, Map<String, String> extra) {
        String result = applyPlaceholders(input, player);
        if (extra != null) {
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                result = result.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return result;
    }

    public static String apply(String input, String... pairs) {
        if (input == null) return "";
        String out = input;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return out;
    }

    public static String formatPlaytime(long minutes) {
        if (minutes <= 0) return "0m";
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long mins = minutes % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0 || sb.isEmpty()) sb.append(mins).append("m");
        return sb.toString().trim();
    }

    public static long ticksToMinutes(long ticks) {
        return Math.max(0L, ticks / 1200L);
    }

    public static List<String> applyPlaceholderList(Player player, List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) out.add(applyPlaceholders(line, player));
        return out;
    }

    public static String time(long seconds) {
        if (seconds <= 0) return "0s";
        long minutes = seconds / 60;
        long remaining = seconds % 60;
        if (remaining == 0) return minutes + "m";
        return minutes + "m " + remaining + "s";
    }

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
