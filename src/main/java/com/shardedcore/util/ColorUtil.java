package com.shardedcore.util;

import net.kyori.adventure.text.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Supports &#RRGGBB, &x&R&R&G&G&B&B, MiniMessage-safe legacy & codes, and §. */
public final class ColorUtil {

    private static final Pattern EXTENDED_HEX = Pattern.compile("(?i)&x((?:&[0-9a-fA-F]){6})");
    private static final Pattern FULL_HEX = Pattern.compile("(?i)&#([0-9a-fA-F]{6})");
    private static final Pattern SHORT_HEX = Pattern.compile("(?i)&#([0-9a-fA-F])(?![0-9a-fA-F])");
    private static final Pattern DOUBLE_AMP = Pattern.compile("&&+");

    private ColorUtil() {
    }

    public static Component parse(String input) {
        return Text.c(input);
    }

    public static Component parsePlain(String input) {
        return Text.cPlain(input);
    }

    public static String normalize(String input) {
        if (input == null || input.isEmpty()) return "";
        String s = input.replace('§', '&');
        s = DOUBLE_AMP.matcher(s).replaceAll("&");
        s = convertExtendedHex(s);
        s = fixShortHex(s);
        return s;
    }

    public static String hexToLegacy(String input) {
        if (input == null || input.isEmpty()) return "";
        String s = normalize(input);
        Matcher matcher = FULL_HEX.matcher(s);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder legacy = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                legacy.append('&').append(c);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(legacy.toString()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String convertExtendedHex(String input) {
        Matcher matcher = EXTENDED_HEX.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String group = matcher.group(1);
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < group.length(); i += 2) {
                if (group.charAt(i) == '&' && i + 1 < group.length()) {
                    hex.append(group.charAt(i + 1));
                }
            }
            if (hex.length() == 6) {
                matcher.appendReplacement(out, Matcher.quoteReplacement("&#" + hex.toString().toUpperCase(Locale.ROOT)));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String fixShortHex(String input) {
        return SHORT_HEX.matcher(input).replaceAll("&$1");
    }

    public static String hex(String raw) {
        if (raw == null) return "";
        String value = raw.trim().replace("#", "").replace("&", "");
        if (value.length() >= 7 && (value.charAt(0) == 'x' || value.charAt(0) == 'X')) {
            value = value.substring(1).replace("&", "");
        }
        if (value.length() != 6) return "";
        try {
            Integer.parseInt(value, 16);
            return value.toUpperCase(Locale.ROOT);
        } catch (NumberFormatException ex) {
            return "";
        }
    }

    public static String gradient(String text, String fromHex, String toHex) {
        if (text == null || text.isEmpty()) return "";
        int[] from = rgb(hex(fromHex));
        int[] to = rgb(hex(toHex));
        if (from == null || to == null) return text;
        String stripped = text.replaceAll("(?i)&x(?:&[0-9a-f]){6}", "")
                .replaceAll("(?i)&#[0-9a-f]{6}", "")
                .replaceAll("(?i)&[0-9a-fk-or]", "");
        int visible = 0;
        for (int i = 0; i < stripped.length(); i++) {
            if (!Character.isWhitespace(stripped.charAt(i))) visible++;
        }
        if (visible == 0) return stripped;
        StringBuilder out = new StringBuilder();
        int index = 0;
        for (int i = 0; i < stripped.length(); i++) {
            char character = stripped.charAt(i);
            if (Character.isWhitespace(character)) {
                out.append(character);
                continue;
            }
            double t = visible == 1 ? 0 : (double) index / (visible - 1);
            int r = (int) Math.round(from[0] + (to[0] - from[0]) * t);
            int g = (int) Math.round(from[1] + (to[1] - from[1]) * t);
            int b = (int) Math.round(from[2] + (to[2] - from[2]) * t);
            out.append(String.format(Locale.ROOT, "&#%02X%02X%02X", r, g, b)).append(character);
            index++;
        }
        return out.toString();
    }

    public static String solid(String text, String hexCode) {
        String value = hex(hexCode);
        if (value.isEmpty() || text == null) return text == null ? "" : text;
        return "&#" + value + text;
    }

    private static int[] rgb(String hex) {
        if (hex == null || hex.length() != 6) return null;
        try {
            return new int[]{
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16)
            };
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
