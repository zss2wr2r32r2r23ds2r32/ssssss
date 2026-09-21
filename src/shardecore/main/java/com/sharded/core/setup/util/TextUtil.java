package com.sharded.core.setup.util;

import com.sharded.core.util.PlaceholderUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtil {

    private static final Pattern HEX_AMP = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_X = Pattern.compile("(?i)&x((?:&[A-Fa-f0-9]){6})");
    private static final Pattern AMP_FORMAT = Pattern.compile("(?i)&([0-9a-fk-or])");
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private TextUtil() {
    }

    
    public static Component component(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_SECTION.deserialize(toSection(input));
    }

    
    public static Component itemComponent(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return withoutDefaultItalic(LEGACY_SECTION.deserialize(toSection(input)));
    }

    
    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return toSection(input);
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Convert legacy {@code &} / {@code &#RRGGBB} colour codes to section-sign form.
     * Hex is converted first so a following {@code &n}/{@code &l} is never eaten by the hex run.
     */
    private static String toSection(String input) {
        String result = input.replace('§', '&');

        Matcher ampHex = HEX_AMP.matcher(result);
        StringBuilder buffer = new StringBuilder();
        int last = 0;
        while (ampHex.find()) {
            buffer.append(result, last, ampHex.start());
            buffer.append(toSectionHex(ampHex.group(1)));
            last = ampHex.end();
        }
        buffer.append(result, last, result.length());
        result = buffer.toString();

        Matcher xHex = HEX_X.matcher(result);
        buffer = new StringBuilder();
        last = 0;
        while (xHex.find()) {
            buffer.append(result, last, xHex.start());
            String digits = xHex.group(1).replace("&", "");
            buffer.append(toSectionHex(digits));
            last = xHex.end();
        }
        buffer.append(result, last, result.length());
        result = buffer.toString();

        Matcher format = AMP_FORMAT.matcher(result);
        buffer = new StringBuilder();
        last = 0;
        while (format.find()) {
            buffer.append(result, last, format.start());
            buffer.append('§').append(format.group(1).toLowerCase());
            last = format.end();
        }
        buffer.append(result, last, result.length());
        return buffer.toString();
    }

    private static String toSectionHex(String hex) {
        StringBuilder out = new StringBuilder(14);
        out.append("§x");
        for (int i = 0; i < 6; i++) {
            out.append('§').append(Character.toLowerCase(hex.charAt(i)));
        }
        return out.toString();
    }

    private static Component withoutDefaultItalic(Component source) {
        if (source instanceof TextComponent textComponent) {
            net.kyori.adventure.text.TextComponent.Builder builder = Component.text()
                    .content(textComponent.content())
                    .style(textComponent.style().decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            for (Component child : source.children()) {
                builder.append(withoutDefaultItalic(child));
            }
            return builder.build();
        }

        List<Component> children = new ArrayList<>();
        for (Component child : source.children()) {
            children.add(withoutDefaultItalic(child));
        }
        return source.children(children)
                .style(source.style().decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE));
    }

    public static String applyPlaceholders(Player player, String input, Map<String, String> extra) {
        if (input == null) {
            return "";
        }
        String result = input;
        if (player != null) {
            result = result.replace("%player%", player.getName());
        }
        if (extra != null) {
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }
        if (player != null) {
            result = PlaceholderUtil.apply(player, result);
        }
        return result;
    }

    public static void sendMessage(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }
        player.sendMessage(component(message));
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(component(message));
    }

    public static void sendActionBar(Player player, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        player.sendActionBar(component(message));
    }
}
