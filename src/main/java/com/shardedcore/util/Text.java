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
}
