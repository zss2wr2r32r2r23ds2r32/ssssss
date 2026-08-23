package dev.sharded.velocitycore.lobby.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextParser {

    private static final Pattern SECTION_HEX = Pattern.compile("§x(§[0-9a-fA-F]){6}");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private TextParser() {
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        Component component = LEGACY.deserialize(convertSectionHex(input));
        return stripItalic(component);
    }

    public static Component parseLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Component.empty();
        }
        List<Component> components = new ArrayList<>();
        for (String line : lines) {
            components.add(parse(line));
        }
        return Component.join(JoinConfiguration.newlines(), components);
    }

    public static String convertSectionHex(String input) {
        Matcher matcher = SECTION_HEX.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String matched = matcher.group();
            StringBuilder hex = new StringBuilder("&#");
            for (int i = 2; i < matched.length(); i += 2) {
                hex.append(matched.charAt(i + 1));
            }
            matcher.appendReplacement(buffer, hex.toString());
        }
        matcher.appendTail(buffer);
        return buffer.toString()
                .replace("<reset>", "&r")
                .replace("<gray>", "&7")
                .replace("<bold>", "&l");
    }

    private static Component stripItalic(Component component) {
        if (component.decorations().containsKey(TextDecoration.ITALIC)
                && component.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE) {
            return component.decoration(TextDecoration.ITALIC, false);
        }
        return component;
    }
}
