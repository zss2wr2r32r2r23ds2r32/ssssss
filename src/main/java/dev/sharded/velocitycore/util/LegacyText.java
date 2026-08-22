package dev.sharded.velocitycore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyText {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private LegacyText() {
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String converted = convertLegacyHex(input);
        Component component = LEGACY.deserialize(converted);
        return stripImplicitDecoration(component);
    }

    private static String convertLegacyHex(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "§x§" + matcher.group(1).charAt(0)
                    + "§" + matcher.group(1).charAt(1)
                    + "§" + matcher.group(1).charAt(2)
                    + "§" + matcher.group(1).charAt(3)
                    + "§" + matcher.group(1).charAt(4)
                    + "§" + matcher.group(1).charAt(5));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static Component stripImplicitDecoration(Component component) {
        if (component.decorations().containsKey(TextDecoration.ITALIC)
                && component.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE) {
            return component.decoration(TextDecoration.ITALIC, false);
        }
        return component;
    }
}
