package dev.sharded.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    private ColorUtil() {
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MINI.deserialize(toMiniMessage(input));
    }

    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    public static String toMiniMessage(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String text = input.replace('§', '&');
        Matcher hex = HEX.matcher(text);
        StringBuilder hexed = new StringBuilder();
        while (hex.find()) {
            hex.appendReplacement(hexed, Matcher.quoteReplacement("<#" + hex.group(1).toUpperCase(Locale.ROOT) + ">"));
        }
        hex.appendTail(hexed);
        text = hexed.toString();

        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                String tag = tagFor(Character.toLowerCase(text.charAt(i + 1)));
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    public static String apply(String template, String... replacements) {
        String result = template == null ? "" : template;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            result = result.replace(replacements[i], replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        return result;
    }

    private static String tagFor(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }
}
