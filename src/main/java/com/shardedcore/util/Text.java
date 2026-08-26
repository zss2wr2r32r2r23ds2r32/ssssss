package com.shardedcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

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

    public static String apply(String input, String... replacements) {
        if (input == null) return "";
        String out = input;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            out = out.replace(replacements[i], replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        return out;
    }
}
