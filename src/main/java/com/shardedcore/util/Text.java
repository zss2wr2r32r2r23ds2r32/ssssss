package com.shardedcore.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

public final class Text {

    private Text() {
    }

    public static Component component(String input) {
        return ColorUtil.parse(input);
    }

    public static Component component(String input, Player player) {
        return component(applyPlaceholders(input, player));
    }

    public static String applyPlaceholders(String input, Player player) {
        if (input == null) {
            return "";
        }
        String result = input;
        if (player != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            result = applyPlaceholderApi(result, player);
        }
        return result;
    }

    public static String applyPlaceholders(String input, Player player, Map<String, String> extra) {
        String result = applyPlaceholders(input, player);
        if (extra != null) {
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                result = result.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return result;
    }

    private static String applyPlaceholderApi(String input, Player player) {
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return (String) papi.getMethod("setPlaceholders", Player.class, String.class)
                    .invoke(null, player, input);
        } catch (ReflectiveOperationException ignored) {
            return input;
        }
    }

    public static String apply(String input, String... pairs) {
        if (input == null) {
            return "";
        }
        String result = input;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result = result.replace(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return result;
    }

    public static String time(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remaining = seconds % 60;
        if (remaining == 0) {
            return minutes + "m";
        }
        return minutes + "m " + remaining + "s";
    }
}
