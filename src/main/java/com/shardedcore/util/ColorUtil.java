package com.shardedcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private ColorUtil() {
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String converted = convertHexToMiniMessage(input);
        if (containsMiniMessageTags(converted)) {
            return MINI_MESSAGE.deserialize(converted).decoration(TextDecoration.ITALIC, false);
        }
        return LEGACY.deserialize(converted).decoration(TextDecoration.ITALIC, false);
    }

    public static String convertHexToMiniMessage(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "<#" + matcher.group(1) + ">");
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static boolean containsMiniMessageTags(String input) {
        return input.indexOf('<') >= 0 && input.indexOf('>') > input.indexOf('<');
    }
}
