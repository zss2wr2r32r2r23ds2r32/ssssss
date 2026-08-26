package com.shardedcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private Text() {
    }

    public static Component c(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        return SERIALIZER.deserialize(ColorUtil.hexToLegacy(input))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Component cPlain(String input) {
        return c(input);
    }

    public static String applyPlaceholders(String input, Player player) {
        if (input == null) return "";
        if (player != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                return (String) papi.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class)
                        .invoke(null, player, input);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (player != null) {
            input = input.replace("%player%", player.getName())
                    .replace("%player_name%", player.getName())
                    .replace("%name%", player.getName());
        }
        return input;
    }

    public static String apply(String input, String... pairs) {
        if (input == null) return "";
        String out = input;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace("%" + pairs[i] + "%", pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return out;
    }

    public static String applyPairs(String input, String... pairs) {
        if (input == null) return "";
        String out = input;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return out;
    }

    public static List<String> applyList(List<String> lines, String... pairs) {
        List<String> out = new ArrayList<>();
        for (String line : lines) out.add(apply(line, pairs));
        return out;
    }

    public static String applyMap(String input, Map<String, String> extra) {
        if (input == null) return "";
        String out = input;
        if (extra != null) {
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                out = out.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return out;
    }

    public static String pretty(String key) {
        String[] parts = key.toLowerCase().replace('_', ' ').replace('-', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
