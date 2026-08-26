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
}
