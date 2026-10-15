package dev.shardedsmp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ColorUtil {
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private ColorUtil() {
    }

    public static Component color(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return SERIALIZER.deserialize(text);
    }

    public static String replace(String text, String placeholder, String value) {
        if (text == null) {
            return "";
        }
        return text.replace(placeholder, value);
    }
}
