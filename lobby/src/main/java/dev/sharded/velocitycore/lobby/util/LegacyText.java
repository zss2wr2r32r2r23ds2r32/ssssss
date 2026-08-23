package dev.sharded.velocitycore.lobby.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class LegacyText {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private LegacyText() {
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        Component component = LEGACY.deserialize(input);
        if (component.decorations().containsKey(TextDecoration.ITALIC)
                && component.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE) {
            return component.decoration(TextDecoration.ITALIC, false);
        }
        return component;
    }
}
